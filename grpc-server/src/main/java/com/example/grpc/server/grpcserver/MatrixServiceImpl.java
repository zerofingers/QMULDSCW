package com.example.grpc.server.grpcserver;


import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Arrays;

// overriding the methods defined in the grpc service interface
// interface defined in grpc-server/src/main/proto/matrix.proto
// this is where the actual work is done
@GrpcService
public class MatrixServiceImpl extends MatrixServiceGrpc.MatrixServiceImplBase
{
	// this is the method that is called when the client calls the server during the addBlock method
	// adds everything in the 2 matrices and returns the result
	@Override
	public void addBlock(MatrixRequest request, StreamObserver<MatrixReply> reply)
	{
		// retrieves request matrices and converts them to lists
		List<Row> a = request.getA().getRowsList();
		List<Row> b = request.getB().getRowsList();

		System.out.println("Request received from client:\n" + request);

		// creates a new matrix to store the result
		int[][] c = new int[a.size()][a.size()];

		// iterates through the matrices and adds the values
		// https://www.javatpoint.com/java-program-to-add-two-matrices
		for(int i=0;i<a.size();i++){   
			List<Integer> arow = a.get(i).getColsList();
			List<Integer> brow = b.get(i).getColsList();
			for(int j=0;j<arow.size();j++){    
				c[i][j]=arow.get(j)+brow.get(j);    
			}      
		}     

		// creates a new matrix reply builder
		Matrix.Builder response = Matrix.newBuilder();

		// iterates through the result matrix and adds the rows
		// uses the same array stream method to convert int[] -> Integer[]
		// elaborated upon in grpc-client/src/main/java/com/example/grpc/client/grpcclient/GRPCClientService.java
		for (int[] row : c) {
			response.addRows(Row.newBuilder().addAllCols(Arrays.stream( row ).boxed().collect( Collectors.toList() )).build());
		}

		// builds the matrix reply with the result matrix
		MatrixReply responseWrapper = MatrixReply.newBuilder().setC(response).build();

		// sends the matrix reply to the client
		reply.onNext(responseWrapper);
		reply.onCompleted();
	}

	// this is the method that is called when the client calls the server during the multiplyBlock method
	// multiplies everything in the 2 matrices and returns the result
	@Override
	public void multiplyBlock(MatrixRequest request, StreamObserver<MatrixReply> reply){
		// retrieves request matrices and converts them to lists
		List<Row> a = request.getA().getRowsList();
		List<Row> b = request.getB().getRowsList();

		System.out.println("Request received from client:\n" + request);

		// creates a new matrix to store the result
		int[][] c = new int[a.size()][a.size()];

		// gets the number of columns in the first matrix
		int row_len = a.get(0).getColsList().size();

		// iterates through the matrices and multiplies the values
		for(int i=0;i<a.size();i++){  
			for(int j=0;j<row_len;j++){    
				c[i][j]=0;    
				List<Integer> a_row = a.get(i).getColsList();  
				for(int k=0;k<row_len;k++){    
					List<Integer> brow = b.get(k).getColsList();    
					c[i][j]+=a_row.get(k)*brow.get(j);      
				} 
			}  
		}    

		// creates a new matrix reply builder
		Matrix.Builder response = Matrix.newBuilder();

		// iterates through the result matrix and adds the rows
		// uses the same array stream method to convert int[] -> Integer[]
		// elaborated upon in grpc-client/src/main/java/com/example/grpc/client/grpcclient/GRPCClientService.java
		for (int[] row : c) {
			response.addRows(Row.newBuilder().addAllCols(Arrays.stream( row ).boxed().collect( Collectors.toList() )).build());
		}

		// builds the matrix reply with the result matrix
		MatrixReply responseWrapper = MatrixReply.newBuilder().setC(response).build();

		// sends the matrix reply to the client
		reply.onNext(responseWrapper);
		reply.onCompleted();
    }
}
