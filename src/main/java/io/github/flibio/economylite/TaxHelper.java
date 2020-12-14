package io.github.flibio.economylite;

import org.spongepowered.api.entity.living.player.User;

public class TaxHelper {
    public static double GetFinalTax(User player, double initialTax) {
        //25% off
        if (player.hasPermission("group.mythical")) {
            return initialTax * 0.75;
        }
        //10% off
        if (player.hasPermission("group.supreme")) {
            return initialTax * 0.90;
        }
        //5% off
        if (player.hasPermission("group.vipplus")) {
            return initialTax * 0.95;
        }
        return initialTax;
    }

    public static double GetAmountSaved(String rank, double initialTax) {
        if (rank.equals("Mythical")) {
            double value = initialTax * 0.75;
            return initialTax - value;
        } else if (rank.equals("Supreme")) {
            double value = initialTax * 0.90;
            return initialTax - value;
        } else {
            double value = initialTax * 0.95;
            return initialTax - value;
        }
    }

    public static String DonorRank(User player) {
        if (player.hasPermission("group.mythical")) {
            return "Mythical";
        }
        //10% off
        if (player.hasPermission("group.supreme")) {
            return "Supreme";
        }
        //5% off
        if (player.hasPermission("group.vipplus")) {
            return "VIP+";
        }
        return "";
    }
}
