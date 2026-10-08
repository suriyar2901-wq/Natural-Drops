package com.naturaldrops.service;

import com.naturaldrops.entity.Seller;
import com.naturaldrops.entity.User;
import com.naturaldrops.exception.BadRequestException;
import com.naturaldrops.repository.SellerRepository;
import com.naturaldrops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountIdentityService {

    private final UserRepository userRepository;
    private final SellerRepository sellerRepository;

    public void rejectDuplicateContact(String phone, String email, Long ignoreUserId, Long ignoreSellerId) {
        String mobile = AccountNotice.digits(phone);
        if (mobile.matches("^[0-9]{10}$") && phoneTaken(mobile, ignoreUserId, ignoreSellerId)) {
            throw new BadRequestException("This mobile number is already used by another account.");
        }
        String mail = email == null ? "" : email.trim();
        if (mail.length() > 0 && emailTaken(mail, ignoreUserId, ignoreSellerId)) {
            throw new BadRequestException("This email is already used by another account.");
        }
    }

    private boolean phoneTaken(String mobile, Long ignoreUserId, Long ignoreSellerId) {
        List<User> users = userRepository.findByPhone(mobile);
        for (User user : users) {
            if (releasedBuyer(user)) {
                continue;
            }
            if (ignoreUserId == null || !ignoreUserId.equals(user.getId())) {
                return true;
            }
        }
        List<Seller> sellers = sellerRepository.findAllByMobile(mobile);
        for (Seller seller : sellers) {
            if (ignoreSellerId == null || !ignoreSellerId.equals(seller.getId())) {
                return true;
            }
        }
        return false;
    }

    private boolean emailTaken(String email, Long ignoreUserId, Long ignoreSellerId) {
        List<User> users = userRepository.findByEmailIgnoreCase(email);
        for (User user : users) {
            if (releasedBuyer(user)) {
                continue;
            }
            if (ignoreUserId == null || !ignoreUserId.equals(user.getId())) {
                return true;
            }
        }
        List<Seller> sellers = sellerRepository.findByEmailIgnoreCase(email);
        for (Seller seller : sellers) {
            if (ignoreSellerId == null || !ignoreSellerId.equals(seller.getId())) {
                return true;
            }
        }
        return false;
    }

    private boolean releasedBuyer(User user) {
        return user.getRole() == User.UserRole.buyer && Boolean.FALSE.equals(user.getIsActive());
    }
}
