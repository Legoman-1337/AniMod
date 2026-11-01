# anyascared.exe — «Осколок памяти» (Акт 6 ARG мини-игра)
# Атмосферный ретро-3D хоррор в стиле Imscared: рейкастинг от первого лица,
# тёмный клаустрофобный лабиринт, фонарик на пару метров, сущность во тьме.
# Цель: найти выход и получить 4-значный код дешифровки для Minecraft (/ani decode).
# Код: 0451 — согласован с модом (ArgFilesManager.ACCESS_CODE).
import pygame
import math
import random
import array
import sys

W, H = 800, 600
FOV = math.pi / 3.0
NUM_RAYS = 320
LIGHT = 3.2          # фонарик: видимость ~2-3 метра
MAP_SIZE = 24

# 1 = стена, 0 = пол, P = старт игрока, S = сущность, E = выход
MAZE = [
    "111111111111111111111111",
    "1P00000001S0000000000001",
    "101111011011110111111101",
    "101000101000010000010001",
    "101011101111010111010111",
    "100010000000010001000001",
    "111010111111010101011101",
    "100010100000010101000101",
    "101110101111110101011101",
    "101000001000000101000001",
    "101011111011111101011111",
    "101010000010000001010001",
    "101010111010111101010101",
    "100010001010100001010101",
    "111110101010101111010101",
    "100000101000100000010101",
    "101111101111101111010101",
    "101000001000100000010001",
    "101011111011101111111101",
    "101010000010001000000001",
    "101110111010101111101011",
    "100000100010100000100E01",
    "111111111111111111111111",
]

CODE = "0451"

WHITE = (220, 220, 225)
DARK = (8, 8, 12)
WALL_TOP = (70, 75, 95)
WALL_BOT = (25, 28, 40)
RED = (180, 20, 20)


def wall_at(x, y):
    if 0 <= int(y) < MAP_SIZE and 0 <= int(x) < MAP_SIZE:
        return MAZE[int(y)][int(x)] == "1"
    return True


class Game:
    def __init__(self):
        pygame.init()
        self.screen = pygame.display.set_mode((W, H))
        pygame.display.set_caption("anyascared.exe")
        self.clock = pygame.time.Clock()
        self.font = pygame.font.SysFont("consolas", 22)
        self.big = pygame.font.SysFont("consolas", 44, bold=True)
        self.state = "intro"  # intro | play | caught | win
        self.reset()

        # Атмосферный звук: низкий гул + стук сердца (генерируются, без файлов)
        try:
            pygame.mixer.init(frequency=22050, size=-16, channels=1, buffer=1024)
            self.hum = self.make_tone(52, 1.2, 0.35)
            self.heart = self.make_tone(46, 0.16, 0.9)
            self.scream = self.make_noise(0.9)
            self.hum.play(loops=-1)
        except Exception:
            self.hum = self.heart = self.scream = None

    # ---------- звук ----------
    def make_tone(self, freq, secs, vol):
        n = int(22050 * secs)
        buf = array.array("h")
        for i in range(n):
            v = int(vol * 32000 * math.sin(2 * math.pi * freq * i / 22050) * (1 - i / n))
            buf.append(v)
        return pygame.mixer.Sound(buffer=buf.tobytes())

    def make_noise(self, secs):
        n = int(22050 * secs)
        buf = array.array("h")
        for i in range(n):
            v = int(random.randint(-20000, 20000) * (1 - i / n))
            buf.append(v)
        return pygame.mixer.Sound(buffer=buf.tobytes())

    # ---------- мир ----------
    def reset(self):
        self.px, self.py = 1.5, 1.5
        self.angle = 0.0
        self.ex, self.ey = 3.5, 1.5          # сущность (P/S строка)
        self.exit_x, self.exit_y = 21.5, 21.5
        self.hunt = False
        self.hunt_timer = random.randint(240, 480)
        self.step_timer = 0
        self.whisper_timer = 300
        self.whisper = ""
        self.caught_t = 0

    # ---------- игрок ----------
    def try_move(self, dx, dy):
        m = 0.2
        if not wall_at(self.px + dx + math.copysign(m, dx), self.py):
            self.px += dx
        if not wall_at(self.px, self.py + dy + math.copysign(m, dy)):
            self.py += dy

    def update_player(self):
        keys = pygame.key.get_pressed()
        spd = 0.035
        if keys[pygame.K_LEFT]:
            self.angle -= 0.04
        if keys[pygame.K_RIGHT]:
            self.angle += 0.04
        mx = pygame.mouse.get_rel()[0]
        self.angle += mx * 0.0025
        self.angle %= math.tau

        dx = dy = 0.0
        if keys[pygame.K_w] or keys[pygame.K_UP]:
            dx += math.cos(self.angle) * spd
            dy += math.sin(self.angle) * spd
        if keys[pygame.K_s] or keys[pygame.K_DOWN]:
            dx -= math.cos(self.angle) * spd
            dy -= math.sin(self.angle) * spd
        if keys[pygame.K_a]:
            dx += math.sin(self.angle) * spd
            dy -= math.cos(self.angle) * spd
        if keys[pygame.K_d]:
            dx -= math.sin(self.angle) * spd
            dy += math.cos(self.angle) * spd
        self.try_move(dx, dy)

        # выход найден
        if abs(self.px - self.exit_x) < 0.6 and abs(self.py - self.exit_y) < 0.6:
            self.state = "win"

    # ---------- сущность ----------
    def update_entity(self):
        dist = math.hypot(self.px - self.ex, self.py - self.ey)
        if not self.hunt:
            self.hunt_timer -= 1
            if self.hunt_timer <= 0 or dist < 5.0:
                self.hunt = True
                if self.heart:
                    self.heart.play()
        else:
            # скрипучий шаг сущности раз в ~0.45 c
            self.step_timer -= 1
            if self.step_timer <= 0:
                self.step_timer = 27
                sp = 0.14
                dx, dy = self.px - self.ex, self.py - self.ey
                steps = [(dx, dy), (dx, 0), (0, dy)]
                for sx, sy in steps:
                    nx, ny = self.ex + math.copysign(min(sp, abs(sx)), sx), \
                             self.ey + math.copysign(min(sp, abs(sy)), sy)
                    if not wall_at(nx, ny):
                        self.ex, self.ey = nx, ny
                        break
            if dist < 0.8:
                self.state = "caught"
                self.caught_t = 90
                if self.scream:
                    self.scream.play()

        # шёпоты, когда близко
        self.whisper_timer -= 1
        if self.whisper_timer <= 0:
            self.whisper_timer = random.randint(240, 500)
            if dist < 7:
                self.whisper = random.choice(["СЗАДИ...", "ОНА СМОТРИТ", "НЕ ОБОРАЧИВАЙСЯ", "ОСТАНОВИСЬ"])
            else:
                self.whisper = ""

    # ---------- рендер ----------
    def cast(self):
        shade_flicker = 0.85 + 0.15 * math.sin(pygame.time.get_ticks() * 0.02) * random.choice([1, 1, 1, 0.6])
        for col in range(NUM_RAYS):
            ray = self.angle - FOV / 2 + FOV * col / NUM_RAYS
            sin_a, cos_a = math.sin(ray), math.cos(ray)
            dist = 0.0
            step = 0.02
            hit = False
            while not hit and dist < LIGHT:
                dist += step
                if wall_at(self.px + cos_a * dist, self.py + sin_a * dist):
                    hit = True
            if not hit:
                continue
            dist *= math.cos(ray - self.angle)
            h = int(H / (dist + 0.0001) * 0.8)
            shade = max(0.0, 1.0 - dist / LIGHT) * shade_flicker
            r = int(WALL_TOP[0] * shade + WALL_BOT[0] * (1 - shade))
            g = int(WALL_TOP[1] * shade + WALL_BOT[1] * (1 - shade))
            b = int(WALL_TOP[2] * shade + WALL_BOT[2] * (1 - shade))
            x = col * W // NUM_RAYS
            w = W // NUM_RAYS + 1
            pygame.draw.rect(self.screen, (r, g, b), (x, (H - h) // 2, w, h))
            # пол и потолок уходят в черноту
            pygame.draw.rect(self.screen, (int(14 * shade), int(14 * shade), int(18 * shade)),
                             (x, (H + h) // 2, w, H // 2))

    def draw_entity(self):
        dx, dy = self.ex - self.px, self.ey - self.py
        dist = math.hypot(dx, dy)
        if dist > LIGHT * 1.4:
            return
        ang = math.atan2(dy, dx) - self.angle
        while ang > math.pi:
            ang -= math.tau
        while ang < -math.pi:
            ang += math.tau
        if abs(ang) > FOV / 2 + 0.2:
            return
        sx = W / 2 + ang / (FOV / 2) * (W / 2)
        size = int(H / (dist + 0.001) * 0.55)
        shade = max(0.15, 1 - dist / LIGHT)
        body = (int(10 * shade + 18), int(8 * shade), int(12 * shade))
        pygame.draw.rect(self.screen, body, (sx - size // 6, H // 2 - size // 3, size // 3, size))
        pygame.draw.circle(self.screen, body, (int(sx), int(H // 2 - size // 3)), size // 7)
        # белые глаза во тьме
        er = max(1, size // 22)
        pygame.draw.circle(self.screen, WHITE, (int(sx - size // 14), int(H // 2 - size // 3 - size // 30)), er)
        pygame.draw.circle(self.screen, WHITE, (int(sx + size // 14), int(H // 2 - size // 3 - size // 30)), er)

    def center_text(self, surf, y, color=WHITE):
        self.screen.blit(surf, (W // 2 - surf.get_width() // 2, y))

    def draw_hud(self):
        if self.whisper:
            surf = self.font.render(self.whisper, True, RED)
            self.screen.blit(surf, (W // 2 - surf.get_width() // 2 + random.randint(-3, 3),
                                    H // 3 + random.randint(-3, 3)))

    # ---------- экраны ----------
    def draw_intro(self):
        self.screen.fill(DARK)
        self.center_text(self.big.render("anyascared.exe", True, (150, 30, 30)), H // 2 - 80)
        self.center_text(self.font.render("восстановление повреждённой памяти...", True, (120, 120, 130)), H // 2 - 10)
        self.center_text(self.font.render("найди выход. она уже знает, что ты здесь.", True, (90, 90, 100)), H // 2 + 30)
        self.center_text(self.font.render("WASD — идти, мышь/стрелки — смотреть. ESC — выход.", True, (70, 70, 80)), H // 2 + 70)

    def draw_caught(self):
        self.caught_t -= 1
        self.screen.fill((0, 0, 0) if self.caught_t % 8 else (120, 0, 0))
        s = min(W, H) * 0.7
        pygame.draw.circle(self.screen, (5, 5, 8), (W // 2, H // 2), int(s // 2))
        pygame.draw.circle(self.screen, WHITE, (W // 2 - s // 7, H // 2 - s // 9), s // 16)
        pygame.draw.circle(self.screen, WHITE, (W // 2 + s // 7, H // 2 - s // 9), s // 16)
        pygame.draw.rect(self.screen, WHITE, (W // 2 - s // 5, H // 2 + s // 6, s // 2.5, s // 22))
        if self.caught_t <= 0:
            self.screen.fill(DARK)
            self.center_text(self.big.render("ОНА НАШЛА ТЕБЯ", True, RED), H // 2 - 60)
            self.center_text(self.font.render("R — попробовать снова, ESC — сдаться", True, (110, 110, 120)), H // 2)
        pygame.display.flip()

    def draw_win(self):
        self.screen.fill(DARK)
        self.center_text(self.big.render("ПАМЯТЬ ВОССТАНОВЛЕНА", True, (60, 200, 120)), H // 2 - 90)
        self.center_text(self.font.render("фрагмент памяти Ани извлечён из дампа.", True, (120, 120, 130)), H // 2 - 20)
        self.center_text(self.big.render("КОД: " + CODE, True, (255, 220, 90)), H // 2 + 30)
        self.center_text(self.font.render("вернись в Minecraft и введи: /ani decode " + CODE, True, (90, 90, 100)), H // 2 + 100)
        self.center_text(self.font.render("ESC — выход", True, (60, 60, 70)), H // 2 + 140)

    # ---------- цикл ----------
    def run(self):
        while True:
            for e in pygame.event.get():
                if e.type == pygame.QUIT or (e.type == pygame.KEYDOWN and e.key == pygame.K_ESCAPE):
                    pygame.quit()
                    sys.exit(0)
                if e.type == pygame.KEYDOWN:
                    if self.state == "intro":
                        self.state = "play"
                    elif self.state == "caught" and self.caught_t <= 0 and e.key == pygame.K_r:
                        self.reset()
                        self.state = "play"

            if self.state == "intro":
                self.draw_intro()
            elif self.state == "caught":
                self.draw_caught()
                self.clock.tick(30)
                continue
            elif self.state == "win":
                self.draw_win()
            else:
                self.screen.fill(DARK)
                self.update_player()
                self.update_entity()
                self.cast()
                self.draw_entity()
                self.draw_hud()

            pygame.display.flip()
            if self.state == "play":
                pygame.mouse.get_rel()
            self.clock.tick(60)


if __name__ == "__main__":
    Game().run()
