package com.github.squi2rel.mcft;

import com.github.squi2rel.mcft.tracking.EyeTrackingRect;
import com.github.squi2rel.mcft.tracking.MouthTrackingRect;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class FTModel {
    public EyeTrackingRect eyeR;
    public EyeTrackingRect eyeL;
    public MouthTrackingRect mouth;
    public boolean isFlat;
    public transient boolean enabled = false;
    public transient long lastReceived = 0;

    public FTModel(EyeTrackingRect eyeR, EyeTrackingRect eyeL, MouthTrackingRect mouth, boolean isFlat) {
        this.eyeR = eyeR;
        this.eyeL = eyeL;
        this.mouth = mouth;
        this.isFlat = isFlat;
    }

    public void update(float fps) {
        float delta = Math.clamp(System.currentTimeMillis() - lastReceived / 1000f * fps, 0, 1);
        eyeR.update(delta);
        eyeL.update(delta);
        if (!isFlat) mouth.update(delta);
    }

    public void readSync(byte[] data) {
        ByteBuf buf = Unpooled.wrappedBuffer(data);
        eyeR.readSync(buf);
        eyeL.readSync(buf);
        mouth.readSync(buf);
        if (buf.readableBytes() != 0) throw new IllegalArgumentException("buffer remaining " + buf.readableBytes() + " bytes");
        lastReceived = System.currentTimeMillis();
    }

    public void validate(boolean init) {
        eyeR.validate(init);
        eyeL.validate(init);
        mouth.validate(init);
    }
}
