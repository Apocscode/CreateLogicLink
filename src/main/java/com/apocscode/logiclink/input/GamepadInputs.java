package com.apocscode.logiclink.input;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

/**
 * Gamepad input reader using GLFW's gamepad API.
 * Ported from Create: Tweaked Controllers for standalone LogicLink operation.
 * <p>
 * Reads 15 buttons and 6 axes from any connected gamepad.
 * Supports auto-detection and manual device selection.
 */
public class GamepadInputs {

    public static final boolean[] buttons = new boolean[15];
    public static final float[] axis = new float[6];

    private static GLFWGamepadState state = null;
    private static int selectedGamepad = -1;
    private static int forcedGamepadIndex = -1;

    public static void ForceSelectGamepad(int index) {
        forcedGamepadIndex = index;
        if (index >= 0 && index < 16 && GLFW.glfwJoystickPresent(index) && GLFW.glfwJoystickIsGamepad(index)) {
            selectedGamepad = index;
        } else if (index < 0) {
            selectedGamepad = -1;
        }
    }

    public static int CycleGamepad() {
        int start = selectedGamepad >= 0 ? selectedGamepad + 1 : 0;
        for (int i = 0; i < 16; i++) {
            int idx = (start + i) % 16;
            if (GLFW.glfwJoystickPresent(idx) && GLFW.glfwJoystickIsGamepad(idx)) {
                ForceSelectGamepad(idx);
                return idx;
            }
        }
        return -1;
    }

    public static void GetControls() {
        checkState();

        if (selectedGamepad < 0) {
            if (forcedGamepadIndex >= 0) {
                if (GLFW.glfwJoystickPresent(forcedGamepadIndex) && GLFW.glfwJoystickIsGamepad(forcedGamepadIndex)) {
                    selectedGamepad = forcedGamepadIndex;
                }
            } else {
                // Auto-detect: find gamepad with button pressed, or unique gamepad
                int uniqueGamepadID = -1;
                for (int i = 0; i < 16 && selectedGamepad < 0; i++) {
                    if (!GLFW.glfwJoystickIsGamepad(i)) continue;
                    if (uniqueGamepadID == -1) {
                        uniqueGamepadID = i;
                    } else if (uniqueGamepadID >= 0) {
                        uniqueGamepadID = -2;
                    }
                    GLFW.glfwGetGamepadState(i, state);
                    for (int b = 0; b < 15; b++) {
                        if (state.buttons(b) == 0) continue;
                        selectedGamepad = i;
                        break;
                    }
                }
                if (selectedGamepad < 0 && uniqueGamepadID >= 0) {
                    selectedGamepad = uniqueGamepadID;
                }
            }
        }

        if (selectedGamepad < 0 || !GLFW.glfwJoystickIsGamepad(selectedGamepad)) {
            empty();
            selectedGamepad = -1;
        } else {
            GLFW.glfwGetGamepadState(selectedGamepad, state);
            fill();
        }
    }

    public static int GetGamepadIndex() {
        return selectedGamepad;
    }

    public static boolean HasGamepad() {
        return selectedGamepad >= 0;
    }

    public static void SearchGamepad() {
        selectedGamepad = -1;
    }

    public static boolean GetButton(int button) {
        return buttons[button];
    }

    public static float GetAxis(int a) {
        return axis[a];
    }

    private static void checkState() {
        if (state == null) {
            state = GLFWGamepadState.create();
        }
    }

    public static void empty() {
        for (int i = 0; i < buttons.length; i++) {
            buttons[i] = false;
        }
        for (int i = 0; i < axis.length; i++) {
            axis[i] = i < 4 ? 0.0f : -1.0f;
        }
    }

    private static void fill() {
        for (int i = 0; i < buttons.length; i++) {
            buttons[i] = state.buttons(i) != 0;
        }
        for (int i = 0; i < axis.length; i++) {
            axis[i] = state.axes(i);
        }
    }

    /** Button names matching CTC's layout (Xbox convention). */
    private static final String[] BUTTON_NAMES = {
        "A", "B", "X", "Y",
        "L Bumper", "R Bumper",
        "Back", "Start", "Guide",
        "L Stick", "R Stick",
        "D-Up", "D-Right", "D-Down", "D-Left"
    };

    /** Axis names matching CTC's layout. */
    private static final String[] AXIS_NAMES = {
        "L Stick X+", "L Stick X-",
        "L Stick Y+", "L Stick Y-",
        "R Stick X+", "R Stick X-",
        "R Stick Y+", "R Stick Y-",
        "L Trigger", "R Trigger"
    };

    public static String GetButtonName(int index) {
        if (index >= 0 && index < BUTTON_NAMES.length) return BUTTON_NAMES[index];
        return "Button " + index;
    }

    public static String GetAxisName(int index) {
        if (index >= 0 && index < AXIS_NAMES.length) return AXIS_NAMES[index];
        return "Axis " + index;
    }
}
