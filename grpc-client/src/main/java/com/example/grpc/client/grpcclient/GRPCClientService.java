package com.example.grpc.client.grpcclient;

import com.example.grpc.server.grpcserver.PingRequest;
import com.example.grpc.server.grpcserver.PongResponse;
import com.example.grpc.server.grpcserver.PingPongServiceGrpc;
import com.example.grpc.server.grpcserver.MatrixRequest;
import com.example.grpc.server.grpcserver.MatrixReply;
import com.example.grpc.server.grpcserver.MatrixServiceGrpc;
import com.example.grpc.server.grpcserver.Matrix;
import com.example.grpc.server.grpcserver.Row;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
public class GRPCClientService {
    public String ping() {
        	ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9090)
                .usePlaintext()
                .build();        
		PingPongServiceGrpc.PingPongServiceBlockingStub stub
                = PingPongServiceGrpc.newBlockingStub(channel);        
		PongResponse helloResponse = stub.ping(PingRequest.newBuilder()
                .setPing("")
                .build());        
		channel.shutdown();        
		return helloResponse.getPong();
    }

	// communication with the server for adding matrices
	public void addBlock(int[][] A, int[][] B, int[][] C, int index, PingPongEndpoint.Config cf){
		// matrix builders setup
		Matrix.Builder A_build = Matrix.newBuilder();
		Matrix.Builder B_build = Matrix.newBuilder();

		//final request builder setup
		MatrixRequest.Builder request = MatrixRequest.newBuilder();
		
		// add A and B to matrix builders (pack matrices into builder package)
		// more context can be provided in: grpc-client/src/main/proto/matrix.proto
		// requires an Integer array to send to the server
		// Arrays.stream( row ).boxed().collect( Collectors.toList() ) converts int[] -> Integer[]
		// https://stackoverflow.com/questions/880581/how-can-i-convert-int-to-integer-in-java
		for (int[] row : A) {
			A_build.addRows(Row.newBuilder().addAllCols(Arrays.stream( row ).boxed().collect( Collectors.toList() )).build());
		}

		for (int[] row : B) {
			B_build.addRows(Row.newBuilder().addAllCols(Arrays.stream( row ).boxed().collect( Collectors.toList() )).build());
		}

		// add the matrices to the request
		request.setA(A_build);
		request.setB(B_build);
		
		// sending request object to the server side
		// loops through channels provided in config
		// config init makes channels from ips provided
		MatrixReply response= cf.stubs.get(index%(cf.channels.size() - 1)).addBlock(
			request.build()
		);

		//get response from response object (from server)
		response.getC();
		Matrix rows = response.getC();

		// operate on bigger matrix using the blocked matrix result
		for (int i = 0; i < rows.getRowsCount(); i++) {
			Row row = rows.getRows(i);
			for (int j = 0; j < row.getColsCount(); j++) {
				C[i][j] = row.getCols(j);
			}
		}
	}

	// main addition method, calls addBlock for each block
	// deadline scaling not implemented for addition
	// much less computationally expensive than multiplication
    public int[][] add(PingPongEndpoint.Config cf){
		int n = cf.getMatrixA().length;

		// current server index
		int index = 0;

		// set up result matrix for return
		int[][] result = new int[n][n];

		// calls addBlock for each block
		for(int i=0;i<n;i++){
			for(int j=0;j<n;j++){
				addBlock(cf.getMatrixA(), cf.getMatrixB(), result, index, cf);
				index++;
			}
		}

		return result;
    }

	// communication with the server for multiplying matrices
	public void multBlock(int[][] A, int[][] B, int[][] C, int index, PingPongEndpoint.Config cf){
		// matrix builders setup
		Matrix.Builder A_build = Matrix.newBuilder();
		Matrix.Builder B_build = Matrix.newBuilder();

		// final request builder setup
		MatrixRequest.Builder request = MatrixRequest.newBuilder();
		
		// add A and B to matrix builders (pack matrices into builder package)
		// more context can be provided in: grpc-client/src/main/proto/matrix.proto
		// requires an Integer array to send to the server
		// Arrays.stream( row ).boxed().collect( Collectors.toList() ) converts int[] -> Integer[]
		// https://stackoverflow.com/questions/880581/how-can-i-convert-int-to-integer-in-java
		for (int[] row : A) {
			A_build.addRows(Row.newBuilder().addAllCols(Arrays.stream( row ).boxed().collect( Collectors.toList() )).build());
		}

		for (int[] row : B) {
			B_build.addRows(Row.newBuilder().addAllCols(Arrays.stream( row ).boxed().collect( Collectors.toList() )).build());
		}

		// add the matrices to the request
		request.setA(A_build);
		request.setB(B_build);
		
		// sending request object to the server side
		MatrixReply response= cf.stubs.get(index%(cf.channels.size() - 1)).multiplyBlock(
			request.build()
		);

		// get response from response object (from server)
		response.getC();
		Matrix rows = response.getC();

		// operate on bigger matrix using the blocked matrix result
		for (int i = 0; i < rows.getRowsCount(); i++) {
			Row row = rows.getRows(i);
			for (int j = 0; j < row.getColsCount(); j++) {
				C[i][j] += row.getCols(j);
			}
		}
	}

	// main multiplication method, calls multBlock for each block
	public int[][] mult(PingPongEndpoint.Config cf){
		// gets full matrix length (for use in splitting up matrix + loops)
		int n = cf.getMatrixA().length;

		//defined deadline for each block
		int deadline = 100;

		// current server index
		int index = 0;

		// set up blocked matrix for calculations
		int[][][][] A_block = new int[2][2][n/2][n/2];
		int[][][][] B_block = new int[2][2][n/2][n/2];

		// block input matrices (into quarters)
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				A_block[i/2][j/2][i%2][j%2] = cf.getMatrixA()[i][j];
				B_block[i/2][j/2][i%2][j%2] = cf.getMatrixB()[i][j];
			}
		}

		// get random number to pick server to ddeadline scale on
		int random = new Random().nextInt((cf.channels.size() - 1));

		// footprinting (using the selected server)
		// setup, similar to multBlock method
		MatrixRequest.Builder request = MatrixRequest.newBuilder();

		// add A and B to matrix builders (pack matrices into builder package)
		Matrix.Builder A = Matrix.newBuilder();
		Matrix.Builder B = Matrix.newBuilder();
		
		// add A and B to matrix builders (pack matrices into builder package)
		for (int[] row : A_block[0][0]) {
			A.addRows(Row.newBuilder().addAllCols(Arrays.stream( row ).boxed().collect( Collectors.toList() )).build());
		}

		for (int[] row : B_block[0][0]) {
			B.addRows(Row.newBuilder().addAllCols(Arrays.stream( row ).boxed().collect( Collectors.toList() )).build());
		}

		// add the matrices to the request
		request.setA(A);
		request.setB(B);

		//start timing
		long startTime = System.nanoTime();
		
		// sending request object to the server side
		// do nothing with reult as it is merely timing
		MatrixReply reply = cf.stubs.get(index%(cf.channels.size() - 1)).multiplyBlock(
			request.build()
		);
		
		//end timing
		long endTime = System.nanoTime();
		double footprint= endTime-startTime;

		//required server calculation
		int numberServer= (int) Math.ceil((footprint*(int) Math.pow(n, 2))/deadline);
		numberServer= numberServer>cf.channels.size()?cf.channels.size():numberServer;

		// if deadline is below a certain threshold, multiplication is unfeasible
		if(deadline <= 70){
			return null;
		}

		// set up a blocked result matrix for return
		int[][][][] C = new int[2][2][n/2][n/2];

		// call multBlock for each block
		multBlock(A_block[0][0], B_block[0][0], C[0][0], index, cf);
		index++;
		multBlock(A_block[0][1], B_block[1][0], C[0][0], index, cf);
		index++;
		multBlock(A_block[0][0], B_block[0][1], C[0][1], index, cf);
		index++;
		multBlock(A_block[0][1], B_block[1][1], C[0][1], index, cf);
		index++;
		multBlock(A_block[1][0], B_block[0][0], C[1][0], index, cf);
		index++;
		multBlock(A_block[1][1], B_block[1][0], C[1][0], index, cf);
		index++;
		multBlock(A_block[1][0], B_block[0][1], C[1][1], index, cf);
		index++;
		multBlock(A_block[1][1], B_block[1][1], C[1][1], index, cf);
		index++;

		//unchunk blocked result matrix
		int[][] out = new int[n][n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				out[i][j] = C[i/2][j/2][i%2][j%2];
			}
		}

		//return result matrix
		return out;
    }
}
