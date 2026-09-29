package com.fridgegatekeeper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 백엔드의 시작점입니다.
 * 이 패키지 아래의 Controller, Service 등을 Spring이 자동으로 찾아 관리합니다.
 */
@SpringBootApplication
public class FridgeGatekeeperApplication {

	public static void main(String[] args) {
		// 내장 웹 서버를 실행하므로 Tomcat을 따로 설치하지 않아도 됩니다.
		SpringApplication.run(FridgeGatekeeperApplication.class, args);
	}

}
