#include <stdio.h>
#include <sys/socket.h>
#include <stdlib.h>
#include <netinet/in.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <netdb.h>
#include <signal.h>
#include <pthread.h>
#include <sys/time.h>


#define MAX 1024

//TO DO: Implement a thread to send a UOK message every 15 seconds to the server
//	 Need to create a thread function for pthread_create
//	 This requires a thread because it needs to run at the same time as the
//	 main thread needs to take data from the stdin and send over to the server
//	 as well as read data from the server.

struct msg {
	short len; //length of total packet in bytes
	short type; //
	char message[1024];
};

struct uok {
	short len;
	short type;
};

int main(int argc, char **argv) {

	if(argc < 2) {
		printf("USAGE: %s host:port\n", argv[0]);
		return 1;
	}
	//one command for host:port
	char *p = strtok(argv[1], ":");
	char *array[2];
	int a = 0;
	while(p != NULL) {
		array[a++] = p;
		p = strtok(NULL, ":");
		//array[0] = localhost
		//array[1] = port
	}

	struct sockaddr_in addr;
	struct sockaddr_in serv_addr;
	struct hostent* server;
	int sockfd;
	int portno;
	struct msg msg;
	struct uok uok; //not complete
	int quit = 1;

	//create socket
	sockfd = socket(AF_INET, SOCK_STREAM, 0);

	if(sockfd == -1) {
		perror("Error occured");
		exit(1);
	}
	else {
		printf("Socket created...\n");
	}

	server = gethostbyname(array[0]);

	if(server == NULL) {
		perror("Error occurred");
		exit(1);
	}

	bzero((char *)&serv_addr, sizeof(serv_addr));
	//assign IP, PORT
	portno = atoi(array[1]);
	serv_addr.sin_family = AF_INET;
	bcopy((char *)server->h_addr, (char *)&serv_addr.sin_addr.s_addr, server->h_length);
	serv_addr.sin_port = htons(portno);


	//connect client socket to server socket
	if(connect(sockfd, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) !=  0) {
		perror("Error occurred");
		exit(1);
	}
	else {
		printf("Connected to server...\n");
	}

	//send client message to server
	while(1) {
		int check;
		//send message to server
		bzero(msg.message, MAX);
		fgets(msg.message, MAX, stdin);
		msg.len = 3 + strlen(msg.message); //not sure why 3 works? tried just strlen, then 1+strlen, 2+strlen, and
						   //3 +strlen was the only one that worked
		msg.type = 2;

		if(strncmp(msg.message, "quit", 4) == 0) {
			break;
		}

		check = write(sockfd, &msg, msg.len);
		if(check < 0) {
			perror("Error writing to server");
		}

		//receive message from server
		bzero(msg.message, MAX);
		check = read(sockfd, &msg, MAX);
		if(check < 0) {
			perror("Error reading from server");
		}
		printf("received: %s\n", msg.message);
	}

	//close the socket when the user enters quit
	close(sockfd);
	return 0;
}
