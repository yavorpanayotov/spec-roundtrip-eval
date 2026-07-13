package com.example.expenses.service;

import com.example.expenses.domain.AppUser;
import com.example.expenses.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<AppUser> listUsers() {
        return userRepository.findAll();
    }

    public Optional<AppUser> findUser(long id) {
        return userRepository.findById(id);
    }
}
