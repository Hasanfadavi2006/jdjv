import pygame
import sys

def main():
    pygame.init()
    pygame.joystick.init()

    count = pygame.joystick.get_count()
    if count == 0:
        print("هیچ جوی‌استیکی پیدا نشد. یه جوی‌استیک وصل کن و دوباره اجرا کن.")
        sys.exit(1)

    joy = pygame.joystick.Joystick(0)
    joy.init()

    print(f"جوی‌استیک شناسایی شد: {joy.get_name()}")
    print(f"تعداد محورها: {joy.get_numaxes()}")
    print(f"تعداد دکمه‌ها: {joy.get_numbuttons()}")
    print("برای خروج Ctrl+C بزن\n")

    clock = pygame.time.Clock()

    try:
        while True:
            for event in pygame.event.get():
                if event.type == pygame.JOYAXISMOTION:
                    val = round(event.value, 3)
                    axis_name = _axis_name(event.axis, joy.get_numaxes())
                    print(f"[محور {axis_name}] مقدار: {val:+.3f}  {_axis_bar(val)}")

                elif event.type == pygame.JOYBUTTONDOWN:
                    print(f"[دکمه {event.button}] فشرده شد  ▼")

                elif event.type == pygame.JOYBUTTONUP:
                    print(f"[دکمه {event.button}] رها شد    ▲")

                elif event.type == pygame.JOYHATMOTION:
                    print(f"[D-PAD] {_hat_direction(event.value)}")

            clock.tick(60)

    except KeyboardInterrupt:
        print("\nخداحافظ!")
    finally:
        joy.quit()
        pygame.quit()


def _axis_name(index, total):
    names = {0: "X چپ", 1: "Y چپ", 2: "X راست", 3: "Y راست", 4: "L2", 5: "R2"}
    return names.get(index, str(index))


def _axis_bar(val):
    filled = int((val + 1) / 2 * 20)
    return "[" + "█" * filled + "░" * (20 - filled) + "]"


def _hat_direction(val):
    x, y = val
    dirs = []
    if y == 1:  dirs.append("بالا ↑")
    if y == -1: dirs.append("پایین ↓")
    if x == -1: dirs.append("چپ ←")
    if x == 1:  dirs.append("راست →")
    return " ".join(dirs) if dirs else "وسط ●"


if __name__ == "__main__":
    main()
