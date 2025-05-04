package com.github.squi2rel.mcft;

import com.github.squi2rel.mcft.tracking.EyeTrackingRect;
import com.github.squi2rel.mcft.tracking.MouthTrackingRect;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class FTModel {
    public EyeTrackingRect eyeR;
    public EyeTrackingRect eyeL;
    public MouthTrackingRect mouth;
    public transient long lastReceived = 0;
    public transient boolean enabled = false;

    public FTModel(EyeTrackingRect eyeR, EyeTrackingRect eyeL, MouthTrackingRect mouth) {
        this.eyeR = eyeR;
        this.eyeL = eyeL;
        this.mouth = mouth;
    }

    @SuppressWarnings("unused")
    public boolean active() {
        return System.currentTimeMillis() - lastReceived < 3000;
    }

    public void readSync(byte[] data) {
        ByteBuf buf = Unpooled.wrappedBuffer(data);
        eyeR.readSync(buf);
        eyeL.readSync(buf);
        mouth.readSync(buf);
        if (buf.readableBytes() != 0) throw new IllegalArgumentException("buffer remaining " + buf.readableBytes() + " bytes");
        lastReceived = System.currentTimeMillis();
    }
}
