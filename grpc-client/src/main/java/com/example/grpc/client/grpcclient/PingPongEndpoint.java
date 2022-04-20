package com.example.grpc.client.grpcclient;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;

import io.grpc.ManagedChannel;
import com.example.grpc.server.grpcserver.MatrixServiceGrpc;
import io.grpc.ManagedChannelBuilder;

import java.util.List;
import java.util.ArrayList;

@Controller
public class PingPongEndpoint {    
	//configuration class definition
	public class Config {
		// matrices used for storing the data
		private int[][] matrixA;
		private int[][] matrixB;

		// IPs of the servers
		//! -------------------------CHANGE THESE VALUES TO YOUR SERVER IPs-------------------------
		String[] ips = new String[]{"34.125.195.23","34.125.40.200","34.125.9.126","34.125.212.127","34.125.60.103","34.125.148.163","34.125.78.145","34.125.127.83"};
		GRPCClientService grpcClientService;
		List<ManagedChannel> channels = new ArrayList<>();
		List<MatrixServiceGrpc.MatrixServiceBlockingStub> stubs = new ArrayList<>();

		// constructor
		// initializes the client service
		// creates the channels and stubs (for communication with thee servers)
		Config(GRPCClientService gCS) {
			grpcClientService = gCS;

			for (int i = 0; i < ips.length; i++) {
				channels.add(ManagedChannelBuilder.forAddress(ips[i], 9090).usePlaintext().build());
				stubs.add(MatrixServiceGrpc.newBlockingStub(channels.get(i)));
			}
		}

		// set empty matrices for a given size (unused)
		public void setMatrices(int k) {
			matrixA = new int[k][k];
			matrixB = new int[k][k];
		}

		// set matrices from predefined values
		public void setMatrices(int[][] A, int[][] B) {
			matrixA = A;
			matrixB = B;
		}

		// getter for matrix A
		public int[][] getMatrixA() {
			return matrixA;
		}

		// getter for matrix B
		public int[][] getMatrixB() {
			return matrixB;
		}

		// setter for matrix A (unused)
		public void setMatrixA(int[][] A) {
			matrixA = A;
		}

		// setter for matrix B (unused)
		public void setMatrixB(int[][] A) {
			matrixA = A;
		}
	}

	//---------configuration object---------
	// + matrix A
	// + matrix B
	// + ips
	// + grpcClientService
	Config cf;

	// endpoint constructor (for Rest Controller)
	@Autowired
	public PingPongEndpoint(GRPCClientService grpcClientService) {
		cf = new Config(grpcClientService);
	}

	//root page (for uploading both matrices)
	@GetMapping("/")
	public String upload() {
		return "upload_matrix";
	}

	//page for displaying both matrices
	//uses the config object to get the matrices
    @GetMapping("/display")
	public String display(Model model) {
		model.addAttribute("A", cf.getMatrixA());
		model.addAttribute("B", cf.getMatrixB());
		return "matrices";
	}

	//page for adding the matrices using grpc and the servers
	@GetMapping("/add")
	public String add(Model model) {
		model.addAttribute("C", cf.grpcClientService.add(cf));
        return "result";
	}
    
	//page for multi the matrices using grpc and the servers
    @GetMapping("/mult")
	public String mult(Model model) {
		model.addAttribute("C", cf.grpcClientService.mult(cf));
        return "result";
	}

	//post request handler (from form in root page - upload_matrix.html)
    @PostMapping("/upload")
	public RedirectView upload(@RequestParam("A") MultipartFile A, @RequestParam("B") MultipartFile B) {
		String A_string = new String(); String B_String = new String(); 

		//checking if the files are empty
        try {
            A_string = new String(A.getBytes()); B_String = new String(B.getBytes());

			System.out.println(A_string);
			System.out.println(B_String);
        } catch (IOException e) {
            System.out.println("Can't read file input stream");
        }

		//matrix extractions
		String[] split_A = A_string.split(";");
        String[] split_B = B_String.split(";");

		int len = split_A.length;

		int[][] matA = new int[len][len];
        int[][] matB = new int[len][len];

		for (int i=0; i < len; i++) {
            split_A[i] = split_A[i].trim();
            split_B[i] = split_B[i].trim();
            String[] single_intA = split_A[i].split("-");
            String[] single_intB = split_B[i].split("-");

            for (int j=0; j < len; j++) {
                matA[i][j] = Integer.parseInt(single_intA[j]);
                matB[i][j] = Integer.parseInt(single_intB[j]);
            }
        }

		//check power of 2
		if (len < 4 || len == (int)Math.pow(2, Math.floor(Math.log(len)/Math.log(2)))) {
			cf.setMatrices(matA, matB);
		}
		else{
			System.out.println("UPLOAD ERROR");
		}

		//redirect to display page (displaying matrices)
		RedirectView rv = new RedirectView();
        rv.setUrl("display");
        return rv;
	}
}
