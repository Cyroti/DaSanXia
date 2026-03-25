package com.example.hdfs.datanode;

import com.example.hdfs.common.Constants;
import com.example.hdfs.rpc.AppendBlockRequest;
import com.example.hdfs.rpc.AppendBlockResponse;
import com.example.hdfs.rpc.DataNodeServiceGrpc;
import com.example.hdfs.rpc.ReadBlockRequest;
import com.example.hdfs.rpc.ReadBlockResponse;
import com.example.hdfs.rpc.ReplicaInfo;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

public class DataNodeServiceImpl extends DataNodeServiceGrpc.DataNodeServiceImplBase {
    private final DataNodeStorage storage;
    private final long artificialDelayMs;
    private final Map<String, DataNodeServiceGrpc.DataNodeServiceBlockingStub> peerStubCache = new HashMap<>();
    private final Map<String, ManagedChannel> peerChannelCache = new HashMap<>();

    public DataNodeServiceImpl(DataNodeStorage storage) {
        this(storage, 0);
    }

    public DataNodeServiceImpl(DataNodeStorage storage, long artificialDelayMs) {
        this.storage = storage;
        this.artificialDelayMs = Math.max(0, artificialDelayMs);
    }

    @Override
    public void readBlock(ReadBlockRequest request, StreamObserver<ReadBlockResponse> responseObserver) {
        maybeDelay();
        byte[] data = storage.read(request.getBlockId());
        if (data == null) {
            responseObserver.onNext(ReadBlockResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("block not found")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        responseObserver.onNext(ReadBlockResponse.newBuilder()
                .setSuccess(true)
                .setMessage("ok")
                .setData(com.google.protobuf.ByteString.copyFrom(data))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void appendBlock(AppendBlockRequest request, StreamObserver<AppendBlockResponse> responseObserver) {
        maybeDelay();

        if (request.getPipelineCount() == 0) {
            responseObserver.onNext(AppendBlockResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("empty pipeline")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        int index = request.getPipelineIndex();
        if (index < 0 || index >= request.getPipelineCount()) {
            responseObserver.onNext(AppendBlockResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("invalid pipeline index")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        ReplicaInfo self = request.getPipeline(index);
        if (index > 0 && !request.getReplicated()) {
            responseObserver.onNext(AppendBlockResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("followers reject direct client writes")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        try {
            int size = storage.append(request.getBlockId(), request.getData().toByteArray());
            byte[] full = storage.read(request.getBlockId());
            String digest = checksum(full == null ? new byte[0] : full);

            ReplicaInfo ack = ReplicaInfo.newBuilder()
                    .setDatanodeId(self.getDatanodeId())
                    .setDatanodeAddress(self.getDatanodeAddress())
                    .setSize(size)
                    .setChecksum(digest)
                    .setUpdateTime(System.currentTimeMillis())
                    .build();

            AppendBlockResponse.Builder response = AppendBlockResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("ok")
                    .setBlockSize(size)
                    .setChecksum(digest)
                    .addAckedReplicas(ack);

            if (index + 1 < request.getPipelineCount()) {
                ReplicaInfo next = request.getPipeline(index + 1);
                try {
                    AppendBlockResponse downstream = dataNode(next.getDatanodeAddress())
                            .withDeadlineAfter(Constants.PIPELINE_FORWARD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                            .appendBlock(request.toBuilder()
                                    .setPipelineIndex(index + 1)
                                    .setReplicated(true)
                                    .build());

                    if (downstream.getSuccess()) {
                        response.addAllAckedReplicas(downstream.getAckedReplicasList());
                    } else {
                        response.setSuccess(false);
                        response.setMessage("replication failed on node=" + next.getDatanodeId());
                    }
                } catch (StatusRuntimeException e) {
                    response.setSuccess(false);
                    response.setMessage("replication timeout on node=" + next.getDatanodeId());
                }
            }

            responseObserver.onNext(response.build());
        } catch (IllegalArgumentException e) {
            responseObserver.onNext(AppendBlockResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build());
        } catch (RuntimeException e) {
            responseObserver.onNext(AppendBlockResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("append failed: " + e.getMessage())
                    .build());
        }
        responseObserver.onCompleted();
    }

    private DataNodeServiceGrpc.DataNodeServiceBlockingStub dataNode(String address) {
        return peerStubCache.computeIfAbsent(address, addr -> {
            String[] hp = splitAddress(addr);
            ManagedChannel ch = ManagedChannelBuilder.forAddress(hp[0], Integer.parseInt(hp[1]))
                    .usePlaintext()
                    .build();
            peerChannelCache.put(addr, ch);
            return DataNodeServiceGrpc.newBlockingStub(ch);
        });
    }

    private static String[] splitAddress(String address) {
        String[] hp = address.split(":");
        if (hp.length != 2) {
            throw new IllegalArgumentException("Invalid address: " + address + ". expected host:port");
        }
        return hp;
    }

    private void maybeDelay() {
        if (artificialDelayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(artificialDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String checksum(byte[] bytes) {
        CRC32 crc32 = new CRC32();
        crc32.update(bytes);
        return Long.toHexString(crc32.getValue());
    }
}
