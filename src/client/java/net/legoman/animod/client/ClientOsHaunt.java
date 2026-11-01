package net.legoman.animod.client;

import net.minecraft.client.Minecraft;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * АКТ 5: «Инфильтрация: Успокой Анечку» — прорыв Ани в Windows.
 * Пока игра свёрнута, каждые ~5 секунд в трей прилетают системные уведомления.
 * Раз в минуту она подменяет буфер обмена своими репликами.
 */
public class ClientOsHaunt {

    private static final String[] MESSAGES = {
            "Успокой анечку...", "Вернись в игру", "Мне холодно", "Я вижу твои файлы", "Не уходи", "Успокой анечку..."
    };

    private static boolean trayReady = false;
    private static TrayIcon trayIcon = null;
    private static int unfocusedTimer = 60;
    private static int clipboardTimer = 0;
    private static int messageIndex = 0;

    private static int act = 0;
    private static boolean coreFixed = false;

    public static void setAct(int actIndex, boolean chatCoreFixed) {
        if (actIndex != act) {
            net.legoman.animod.AniMod.LOGGER.info("[Haunt] act={}, coreFixed={}, headless={}",
                    actIndex, chatCoreFixed, java.awt.GraphicsEnvironment.isHeadless());
        }
        act = actIndex;
        coreFixed = chatCoreFixed;
    }

    public static void tick(Minecraft mc) {
        boolean haunt = act >= 5 && act <= 7 && !coreFixed;
        if (!haunt) {
            // Аня ушла из системы — убираем свой значок из трея
            if (trayReady) {
                try {
                    SystemTray.getSystemTray().remove(trayIcon);
                } catch (Throwable ignored) {}
                trayReady = false;
                trayIcon = null;
            }
            unfocusedTimer = 60;
            clipboardTimer = 0;
            return;
        }

        if (!mc.isWindowActive()) {
            if (++unfocusedTimer >= 100) { // ~5 секунд
                unfocusedTimer = 0;
                showNotification();
            }
        } else {
            unfocusedTimer = 60;
        }

        if (++clipboardTimer >= 1200) { // ~раз в минуту
            clipboardTimer = 0;
            net.legoman.animod.AniMod.LOGGER.info("[Haunt] пишу в буфер обмена...");
            writeClipboard();
        }
    }

    private static void showNotification() {
        try {
            if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) return;
            if (!trayReady) {
                trayIcon = new TrayIcon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB));
                trayIcon.setImageAutoSize(true);
                SystemTray.getSystemTray().add(trayIcon);
                trayReady = true;
            }
            trayIcon.displayMessage("Аня", MESSAGES[messageIndex++ % MESSAGES.length], TrayIcon.MessageType.WARNING);
        } catch (Throwable ignored) {
            // AWT может быть недоступен — молча пропускаем
        }
    }

    private static void writeClipboard() {
        try {
            if (GraphicsEnvironment.isHeadless()) return;
            String phrase = MESSAGES[messageIndex++ % MESSAGES.length];
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(phrase), null);
        } catch (Throwable ignored) {}
    }
}
