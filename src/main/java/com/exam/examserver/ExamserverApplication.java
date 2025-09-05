package com.exam.examserver;

import com.exam.examserver.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ExamserverApplication implements CommandLineRunner {

	@Autowired
	private UserService userService;

	public static void main(String[] args) {
		SpringApplication.run(ExamserverApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		System.out.println("starting code");

//		User user = new User();
//		user.setStudentCode("B22DCCN384");
//		user.setPassword("pass");
//		user.setUsername("admin");
//		user.setFirstName("Huy");
//		user.setLastName("Nguyen Doan");
//		user.setEmail("huynguyendoan0305@gmail.com");
//		user.setPhone("0975796204");
//		user.setGender(Gender.MALE);
//		user.setBirthDate(LocalDate.of(2004, 5, 3));
//		user.setMajor("CNTT");
//		user.setClassName("D22CQCN12-B");
//
//		Role role = new Role(1L, RoleType.ADMIN);
//		UserRole userRole = new UserRole();
//		userRole.setRole(role);
//		userRole.setUser(user);
//
//		Role role2 = new Role(2L, RoleType.NORMAL);
//		UserRole userRole2 = new UserRole();
//		userRole2.setRole(role2);
//		userRole2.setUser(user);
//
//		Set<UserRole> userRoleSet = new HashSet<>();
//		userRoleSet.add(userRole);
//		userRoleSet.add(userRole2);
//
//		User user1 = this.userService.createUser(user, userRoleSet);
//		System.out.println(user1.getUsername());

	}
}
