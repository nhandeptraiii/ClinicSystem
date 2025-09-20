package vn.project.ClinicSystem.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import vn.project.ClinicSystem.model.User;
import vn.project.ClinicSystem.repository.UserRepository;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User handleCreateUser(User user) {
        return this.userRepository.save(user);
    }

    public User handleGetUserById(Long id) {
        Optional<User> userOptional = this.userRepository.findById(id);
        if (userOptional.isPresent()) {
            return userOptional.get();
        }
        return null;
    }

    public List<User> fetchGetAllUsers() {
        return this.userRepository.findAll();
    }

    public void handleDeleteUserById(Long id) {
        this.userRepository.deleteById(id);
    }

    public User handleUpdateUserById(User reqUser) {
        User currentUser = this.handleGetUserById(reqUser.getId());
        if (currentUser != null) {
            currentUser.setName(reqUser.getName());
            currentUser.setEmail(reqUser.getEmail());
            currentUser.setPassword(reqUser.getPassword());

            currentUser = this.userRepository.save(currentUser);
        }
        return currentUser;
    }

    public User handlegetUserbyUsername(String username) {
        return this.userRepository.findByEmail(username);
    }
}
