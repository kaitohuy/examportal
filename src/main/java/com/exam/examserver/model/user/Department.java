package com.exam.examserver.model.user;

import com.exam.examserver.model.exam.Subject;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "department")
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String name;
    private String description;

    // Đây là owning side: chứa khóa ngoại head_user_id
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "head_user_id", nullable = true)
    private User headUser;

    //inverse side: subject
    @OneToMany(mappedBy = "department", fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<Subject> subjects = new HashSet<>();

    // getters & setters

    public Department() {}

    public Department(Long department_id, String name, String description, Set<Subject> subjects, User headUser) {
        this.id = department_id;
        this.name = name;
        this.description = description;
        this.subjects = subjects;
        this.headUser = headUser;
    }

    public Long getId() {
        return id;
    }

    public void setDepartment_id(Long department_id) {
        this.id = department_id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public User getHeadUser() {
        return headUser;
    }

    public void setHeadUser(User headUser) {
        this.headUser = headUser;
    }

    public Set<Subject> getSubjects() {
        return subjects;
    }

    public void setSubjects(Set<Subject> subjects) {
        this.subjects = subjects;
    }
}

