import socket
import time

import RPi.GPIO as GPIO

# 舵机配置
SERVO_PIN = 12  # BCM编码的GPIO18
PWM_FREQ = 50  # 舵机PWM频率固定为50Hz
# MIN_DUTY = 2.5  # 0度对应的占空比
# MAX_DUTY = 12.5  # 180度对应的占空比
MIN_DUTY = 0.5 / 20 * 100  # 0度对应的占空比
MAX_DUTY = 2.5 / 20 * 100  # 180度对应的占空比


# 初始化GPIO和PWM
def init_servo():
    GPIO.setmode(GPIO.BCM)
    GPIO.setup(SERVO_PIN, GPIO.OUT)
    # 创建PWM对象，设置频率
    pwm = GPIO.PWM(SERVO_PIN, PWM_FREQ)
    pwm.start(0)  # 初始占空比0，舵机不转动
    return pwm


# 将角度转换为对应的占空比
def angle_to_duty(angle):
    # 角度范围限制在0-180度
    angle = max(0, min(180, angle))
    # 线性映射：角度→占空比
    duty = MIN_DUTY + (angle / 180.0) * (MAX_DUTY - MIN_DUTY)
    return duty


# 控制舵机转到指定角度
def set_servo_angle(pwm, angle):
    duty = angle_to_duty(angle)
    pwm.ChangeDutyCycle(duty)
    time.sleep(1)  # 给舵机足够的转动时间
    pwm.ChangeDutyCycle(0)  # 停止PWM信号，减少抖动


# 启动TCP服务端
def start_server():
    # 初始化舵机
    pwm = init_servo()

    # 创建TCP socket
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    # 允许端口复用，避免程序重启后端口被占用
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    # 绑定地址（空字符串表示监听所有网卡，端口设为8888）
    server_socket.bind(("", 8888))
    # 开始监听，最大连接数1
    server_socket.listen(1)
    print(f"树莓派舵机控制服务已启动，监听端口8888...")

    try:
        while True:
            # 等待客户端连接
            client_socket, client_addr = server_socket.accept()
            print(f"已连接客户端: {client_addr}")

            try:
                # 接收客户端数据（最多1024字节）
                data = client_socket.recv(1024).decode("utf-8").strip()
                if not data:
                    continue

                # 解析角度指令（格式：angle=90）
                if data.startswith("angle="):
                    try:
                        angle = float(data.split("=")[1])
                        print(f"接收到指令：控制舵机转到 {angle} 度")
                        # 控制舵机转动
                        set_servo_angle(pwm, angle)
                        # 向客户端返回成功信息
                        client_socket.sendall(
                            f"成功：舵机已转到 {angle} 度".encode("utf-8")
                        )
                    except ValueError:
                        # 角度解析失败
                        client_socket.sendall("错误：角度值必须是数字".encode("utf-8"))
                else:
                    # 指令格式错误
                    client_socket.sendall(
                        "错误：指令格式应为 angle=数值（如 angle=90）".encode("utf-8")
                    )
            except Exception as e:
                print(f"处理客户端请求出错：{e}")
            finally:
                # 关闭客户端连接
                client_socket.close()
                print(f"与客户端 {client_addr} 的连接已关闭")
    except KeyboardInterrupt:
        print("\n程序被手动终止")
    finally:
        # 清理资源
        pwm.stop()
        GPIO.cleanup()
        server_socket.close()
        print("资源已释放，程序退出")


if __name__ == "__main__":
    start_server()
