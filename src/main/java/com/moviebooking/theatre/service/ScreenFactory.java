package com.moviebooking.theatre.service;

import com.moviebooking.exception.BusinessException;
import com.moviebooking.theatre.entity.PremiumScreen;
import com.moviebooking.theatre.entity.Screen;
import com.moviebooking.theatre.entity.StandardScreen;
import com.moviebooking.theatre.entity.VipScreen;
import org.springframework.stereotype.Component;

/** FACTORY: creates the correct Screen subclass from the type code sent by the UI. */
@Component
public class ScreenFactory {

    public Screen create(String screenType) {
        String code = screenType == null ? "" : screenType.trim().toUpperCase();
        return switch (code) {
            case "STANDARD" -> new StandardScreen();
            case "PREMIUM" -> new PremiumScreen();
            case "VIP" -> new VipScreen();
            default -> throw new BusinessException("Unknown screen type: " + screenType
                    + " (use STANDARD, PREMIUM or VIP)");
        };
    }
}
