package com.example.hdfs.namenode;

import com.example.hdfs.rpc.AllocateBlockRequest;
import com.example.hdfs.rpc.AllocateBlockResponse;
import com.example.hdfs.rpc.BlockInfo;
import com.example.hdfs.rpc.CloseRequest;
import com.example.hdfs.rpc.CloseResponse;
import com.example.hdfs.rpc.NameNodeServiceGrpc;
import com.example.hdfs.rpc.OpenRequest;
import com.example.hdfs.rpc.OpenResponse;
import com.example.hdfs.rpc.ReplicaInfo;
import com.example.hdfs.rpc.UpdateBlockRequest;
import com.example.hdfs.rpc.UpdateBlockResponse;
import io.grpc.stub.StreamObserver;

public class NameNodeServiceImpl extends NameNodeServiceGrpc.NameNodeServiceImplBase {
    private final MetadataManager metadataManager;

    public NameNodeServiceImpl(MetadataManager metadataManager) {
        this.metadataManager = metadataManager;
    }

    @Override
    public void open(OpenRequest request, StreamObserver<OpenResponse> responseObserver) {
        MetadataManager.OpenResult result = metadataManager.open(request.getPath(), request.getMode());
        OpenResponse response = OpenResponse.newBuilder()
                .setSuccess(result.success())
                .setMessage(result.message())
                .setMetadata(result.metadata())
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void close(CloseRequest request, StreamObserver<CloseResponse> responseObserver) {
        boolean ok = metadataManager.close(request.getPath());
        responseObserver.onNext(CloseResponse.newBuilder()
                .setSuccess(ok)
                .setMessage(ok ? "ok" : "file not found")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void allocateBlock(AllocateBlockRequest request, StreamObserver<AllocateBlockResponse> responseObserver) {
        BlockRecord block = metadataManager.allocateBlock(request.getPath());
        if (block == null) {
            responseObserver.onNext(AllocateBlockResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("allocate failed: file not found or file is not opened in write mode")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        BlockInfo.Builder blockInfoBuilder = BlockInfo.newBuilder()
            .setBlockId(block.getBlockId())
            .setSize(block.getSize());

        for (ReplicaRecord r : block.getReplicas()) {
            blockInfoBuilder.addReplicas(ReplicaInfo.newBuilder()
                .setDatanodeId(r.getDataNodeId())
                .setDatanodeAddress(r.getDataNodeAddress())
                .setSize(r.getSize())
                .setChecksum(r.getChecksum() == null ? "" : r.getChecksum())
                .setUpdateTime(r.getUpdateTime())
                .build());
        }

        responseObserver.onNext(AllocateBlockResponse.newBuilder()
                .setSuccess(true)
                .setMessage("ok")
            .setBlock(blockInfoBuilder.build())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void updateBlock(UpdateBlockRequest request, StreamObserver<UpdateBlockResponse> responseObserver) {
        boolean ok = metadataManager.updateBlockReplica(request.getPath(), request.getBlockId(), request.getReplica());
        responseObserver.onNext(UpdateBlockResponse.newBuilder()
                .setSuccess(ok)
            .setMessage(ok ? "ok" : "update failed: file not found, replica invalid, or file is not opened in write mode")
                .build());
        responseObserver.onCompleted();
    }
}
