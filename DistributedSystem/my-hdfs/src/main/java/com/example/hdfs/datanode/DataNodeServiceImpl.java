package com.example.hdfs.datanode;

import com.example.hdfs.rpc.AppendBlockRequest;
import com.example.hdfs.rpc.AppendBlockResponse;
import com.example.hdfs.rpc.DataNodeServiceGrpc;
import com.example.hdfs.rpc.ReadBlockRequest;
import com.example.hdfs.rpc.ReadBlockResponse;
import io.grpc.stub.StreamObserver;

public class DataNodeServiceImpl extends DataNodeServiceGrpc.DataNodeServiceImplBase {
    private final DataNodeStorage storage;

    public DataNodeServiceImpl(DataNodeStorage storage) {
        this.storage = storage;
    }

    @Override
    public void readBlock(ReadBlockRequest request, StreamObserver<ReadBlockResponse> responseObserver) {
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
        try {
            int size = storage.append(request.getBlockId(), request.getData().toByteArray());
            responseObserver.onNext(AppendBlockResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("ok")
                    .setBlockSize(size)
                    .build());
        } catch (IllegalArgumentException e) {
            responseObserver.onNext(AppendBlockResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build());
        }
        responseObserver.onCompleted();
    }
}
