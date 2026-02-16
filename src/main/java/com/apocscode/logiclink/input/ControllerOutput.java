package com.apocscode.logiclink.input;

/**
 * Encodes/decodes controller button and axis state for network transmission.
 * Ported from CTC's ControllerRedstoneOutput.
 * <p>
 * Buttons (15): packed into a short, one bit each.
 * Axes (6): 4 joystick axes (5 bits each: 1 sign + 4 magnitude) + 2 triggers (4 bits each, unsigned).
 */
public class ControllerOutput {

    public final boolean[] buttons = new boolean[15];
    public final byte[] axis = new byte[6];
    public final float[] fullAxis = new float[6];

    public void clear() {
        for (int i = 0; i < buttons.length; i++) buttons[i] = false;
        for (int i = 0; i < axis.length; i++) { axis[i] = 0; fullAxis[i] = 0; }
    }

    /** Pack 15 buttons into a short (one bit each). */
    public short encodeButtons() {
        short result = 0;
        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i]) result |= (1 << i);
        }
        return result;
    }

    /** Unpack 15 buttons from a short. */
    public void decodeButtons(short value) {
        for (int i = 0; i < buttons.length; i++) {
            buttons[i] = ((value & (1 << i)) != 0);
        }
    }

    /** Pack 6 axes into an int. */
    public int encodeAxis() {
        int result = 0;
        for (int i = 0; i < axis.length; i++) {
            if (i < 4) {
                result |= (axis[i] & 0x1F) << (i * 5);
            } else {
                result |= (axis[i] & 0x0F) << (i == 4 ? 20 : 24);
            }
        }
        return result;
    }

    /** Unpack 6 axes from an int. */
    public void decodeAxis(int value) {
        for (int i = 0; i < axis.length; i++) {
            if (i < 4) {
                axis[i] = (byte) ((value >>> (i * 5)) & 0x1F);
            } else {
                int shift = i == 4 ? 20 : 24;
                axis[i] = (byte) ((value >>> shift) & 0x0F);
            }
        }
    }

    /**
     * Fill the output from GamepadInputs current state.
     * Converts float axis values to encoded byte representation.
     */
    public void fillFromGamepad(boolean useFullPrecision) {
        for (int i = 0; i < buttons.length; i++) {
            buttons[i] = GamepadInputs.GetButton(i);
        }
        for (int i = 0; i < axis.length; i++) {
            if (i >= 4) { // triggers
                float v = (GamepadInputs.GetAxis(i) + 1) / 2;
                v = Math.max(0, Math.min(1, v));
                if (useFullPrecision) fullAxis[i] = v;
                axis[i] = (byte) Math.round(v * 15);
            } else { // joystick axes
                float v = GamepadInputs.GetAxis(i);
                boolean negative = v < 0;
                if (negative) v = -v;
                v = Math.max(0, Math.min(1, v));
                if (useFullPrecision) fullAxis[i] = negative ? -v : v;
                axis[i] = (byte) Math.round(v * 15);
                if (negative && axis[i] > 0) axis[i] = (byte) (axis[i] + 16);
            }
        }
    }
}
