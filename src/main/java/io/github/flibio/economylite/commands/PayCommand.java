/*
 * This file is part of EconomyLite, licensed under the MIT License (MIT). See the LICENSE file at the root of this project for more information.
 */
package io.github.flibio.economylite.commands;

import io.github.flibio.economylite.EconomyLite;
import io.github.flibio.economylite.TaxHelper;
import io.github.flibio.economylite.TextUtils;
import io.github.flibio.utils.commands.AsyncCommand;
import io.github.flibio.utils.commands.BaseCommandExecutor;
import io.github.flibio.utils.commands.Command;
import io.github.flibio.utils.message.MessageStorage;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.command.spec.CommandSpec.Builder;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.EventContext;
import org.spongepowered.api.service.economy.EconomyService;
import org.spongepowered.api.service.economy.account.UniqueAccount;
import org.spongepowered.api.service.economy.transaction.ResultType;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

@AsyncCommand
@Command(aliases = {"pay"}, permission = "economylite.pay")
public class PayCommand extends BaseCommandExecutor<Player> {

    private MessageStorage messageStorage = EconomyLite.getMessageStorage();
    private EconomyService ecoService = EconomyLite.getEconomyService();
    private final String CurrencyName = ecoService.getDefaultCurrency().getPluralDisplayName().toPlain();

    @Override
    public Builder getCommandSpecBuilder() {
        return CommandSpec.builder()
                .executor(this)
                .arguments(GenericArguments.user(Text.of("player")), GenericArguments.doubleNum(Text.of("amount")));
    }

    @Override
    public void run(Player src, CommandContext args) {
        if (args.getOne("player").isPresent() && args.getOne("amount").isPresent()) {
            BigDecimal amount = BigDecimal.valueOf(args.<Double>getOne("amount").get());
            if (amount.intValue() < 100) {
                src.sendMessage(Text.of(TextColors.RED, "You can't send less than 100 " + ecoService.getDefaultCurrency().getPluralDisplayName().toPlain() + "!"));
                return;
            }
            double taxpercent = EconomyLite.getConfigManager().getValue(Double.class, "tax-percentage").orElse(15.0);
            double taxed = amount.doubleValue() * (taxpercent / 100.0f);
            if (amount.doubleValue() <= 0) {
                src.sendMessage(messageStorage.getMessage("command.pay.invalid"));
            } else if (args.<User>getOne("player").isPresent()) {
                User target = args.<User>getOne("player").get();
                double finalTaxed = TaxHelper.GetFinalTax(src, taxed);
                if (target.hasPermission("economylite.blockpayments") && !target.hasPermission("*")) {
                    src.sendMessage(Text.of(TextColors.RED, "This user is blocked from receiving payments. Most likely due to breaking rule 10."));
                    return;
                }
                if (src.hasPermission("economylite.blockpayments") && !src.hasPermission("*")) {
                    src.sendMessage(Text.of(TextColors.RED, "You are blocked from sending payments. Most likely due to breaking rule 10."));
                    return;
                }
                int lottery = (int) (finalTaxed * (10 / 100.0f));
                String rank = TaxHelper.DonorRank(src);
                String added = "";
                if (!rank.isEmpty()) {
                    added = " (" + TaxHelper.PercentOff(rank) + "% off due to you having " + rank + ")";
                }
                src.sendMessage(Text.of(TextColors.WHITE, " You are about to send ", TextColors.GOLD, String.format(Locale.ENGLISH, "%,.2f", amount) + " " + CurrencyName, TextColors.WHITE, " to ", TextColors.GOLD,
                        target.getName(), TextColors.WHITE, " and are being taxed an additional ", TextColors.GOLD, String.format(Locale.ENGLISH, "%,.2f", finalTaxed) + " " + CurrencyName, TextColors.WHITE, added + " " +
                                "for the transfer. As such the total amount being deducted from your account will be ", TextColors.GOLD,
                        String.format(Locale.ENGLISH, "%,.2f", amount.doubleValue() + finalTaxed) + " " + CurrencyName, TextColors.WHITE, ". Due to your transaction ",
                        TextColors.GOLD, lottery + " " + CurrencyName, TextColors.WHITE, " will be added to the lottery. Please confirm by clicking below!"));
                src.sendMessage(TextUtils.yesOrNo(c -> {
                    pay(target, amount, src, finalTaxed);
                }, c -> {
                    src.sendMessage(messageStorage.getMessage("command.pay.confirmno", "player", target.getName()));
                }));
            }
        } else {
            src.sendMessage(messageStorage.getMessage("command.error"));
        }
    }

    private void pay(User target, BigDecimal amount, Player src, double taxed) {
        String targetName = target.getName();
        if (!target.getUniqueId().equals(src.getUniqueId())) {
            Optional<UniqueAccount> uOpt = ecoService.getOrCreateAccount(src.getUniqueId());
            Optional<UniqueAccount> tOpt = ecoService.getOrCreateAccount(target.getUniqueId());
            if (uOpt.isPresent() && tOpt.isPresent()) {
                if (uOpt.get().getBalance(ecoService.getDefaultCurrency()).doubleValue() < amount.doubleValue() + taxed) {
                    src.sendMessage(Text.of(TextColors.RED, "You don't have enough money to cover the payment (" + amount.doubleValue(), ") and the tax (" + taxed + ")!"));
                    return;
                }
                if (uOpt.get().withdraw(ecoService.getDefaultCurrency(), BigDecimal.valueOf(taxed), Cause.of(EventContext.empty(), (EconomyLite.getInstance()))).getResult().equals(ResultType.SUCCESS) && uOpt.get().transfer(tOpt.get(), ecoService.getDefaultCurrency(), amount, Cause.of(EventContext.empty(), (EconomyLite.getInstance()))).getResult().equals(ResultType.SUCCESS)) {
                    Text label = ecoService.getDefaultCurrency().getPluralDisplayName();
                    if (amount.equals(BigDecimal.ONE)) {
                        label = ecoService.getDefaultCurrency().getDisplayName();
                    }
                    src.sendMessage(messageStorage.getMessage("command.pay.success", "target", targetName, "amountandlabel",
                            String.format(Locale.ENGLISH, "%,.2f", amount) + " " + label.toPlain()));
                    final Text curLabel = label;
                    Sponge.getServer().getPlayer(target.getUniqueId()).ifPresent(p -> {
                        p.sendMessage(messageStorage.getMessage("command.pay.target", "amountandlabel",
                                String.format(Locale.ENGLISH, "%,.2f", amount) + " " + curLabel.toPlain(), "sender",
                                uOpt.get().getDisplayName().toPlain()));
                    });
                    /*
                    String rank = TaxHelper.DonorRank(src);
                    if (!rank.isEmpty()) {
                        src.sendMessage(Text.of(TextColors.GREEN, "You just saved ", TextColors.GOLD, String.format(Locale.ENGLISH, "%,.2f", TaxHelper.GetAmountSaved(rank, taxed)), TextColors.GREEN, " BacoBits due to being a ", TextColors.GOLD, rank, TextColors.GREEN,
                                " rank owner!"));
                    }*/
                    int lottery = (int) (taxed * (10 / 100.0f));
                    Sponge.getServer().getConsole().sendMessage(Text.of(TextColors.GREEN, src.getName(), TextColors.GOLD, " has paid ", TextColors.GREEN, amount + " BacoBits", TextColors.GOLD, " to ", TextColors.GREEN, target.getName()));
                    //src.sendMessage(Text.of(TextColors.GREEN, "Due to you paying ", TextColors.GOLD, targetName, TextColors.GOLD, " " + lottery, TextColors.GREEN, " BacoBits were added to the lottery pot!"));
                    Sponge.getCommandManager().process(Sponge.getServer().getConsole(), "lot addpot " + lottery);
                } else {
                    src.sendMessage(messageStorage.getMessage("command.pay.failed", "target", targetName));
                }
            } else {
                src.sendMessage(messageStorage.getMessage("command.error"));
            }
        } else {
            src.sendMessage(messageStorage.getMessage("command.pay.notyou"));
        }
    }
}
