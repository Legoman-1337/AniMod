package net.legoman.animod.story;

import net.minecraft.server.MinecraftServer;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * АКТ 7, EXTREME-версия: РЕАЛЬНЫЙ синий экран смерти Windows.
 * Работает ТОЛЬКО если:
 *   1) игра запущена от имени Администратора (иначе привилегия не поднимется),
 *   2) файл-флаг config/animod_extreme_bsod.txt существует.
 * Нет файла → Lite-версия (фейковый BSOD-оверлей) — это режим для публики.
 *
 * Вернуть фейк для себя: просто удалить файл config/animod_extreme_bsod.txt.
 *
 * Механика: PowerShell (наследует права игры) вызывает ntdll RtlAdjustPrivilege
 * (SeDebugPrivilege) + NtRaiseHardError — ядро Windows выполняет bugcheck.
 * Мир уже сохранён (saveAllChunks вызывается до этого).
 */
public class HardCrash {

    private static final String SCRIPT =
            "$s='using System;using System.Runtime.InteropServices;public class ACrash{" +
            "[DllImport(\"ntdll.dll\")]public static extern uint RtlAdjustPrivilege(int p,bool e,bool c,out bool o);" +
            "[DllImport(\"ntdll.dll\")]public static extern uint NtRaiseHardError(uint e,uint n,uint[] m,uint[] p,uint o,out uint r);}';" +
            "Add-Type -TypeDefinition $s;" +
            "[ACrash]::RtlAdjustPrivilege(19,$true,$false,[ref]$false)|Out-Null;" +
            "$r=0;[ACrash]::NtRaiseHardError(0xDEADBEEF,0,$null,$null,6,[ref]$r)";

    public static boolean isExtremeEnabled(MinecraftServer server) {
        try {
            Path flag = server.getServerDirectory().toPath().resolve("config").resolve("animod_extreme_bsod.txt");
            return Files.exists(flag);
        } catch (Exception e) {
            return false;
        }
    }

    /** Запускает реальный BSOD. Вызывается ПОСЛЕ сохранения всех чанков. */
    public static void trigger() {
        try {
            new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", SCRIPT)
                    .start();
        } catch (Exception ignored) {
            // Не хватило прав — падать нечестно нельзя, но и без последствий: мир уже сохранён
        }
    }
}
