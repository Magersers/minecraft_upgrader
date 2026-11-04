package dev.upgrade.client;

import java.nio.file.*;

/** Deterministic UI invariants, executable without starting Minecraft. */
public final class UpgradeUiMathTest {
    private static int checks;
    private static void check(boolean ok,String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static void close(double a,double b,String message) { check(Math.abs(a-b)<1e-10,message); }
    public static void main(String[] args) throws Exception {
        close(UpgradeUiMath.angleAt(0,-10),0,"top");
        close(UpgradeUiMath.angleAt(10,0),Math.PI/2,"right");
        close(UpgradeUiMath.angleAt(0,10),Math.PI,"bottom");
        close(UpgradeUiMath.angleAt(-10,0),Math.PI*1.5,"left");
        close(UpgradeUiMath.normalize(-Math.PI/2),Math.PI*1.5,"negative wrap");
        close(UpgradeUiMath.normalize(Double.NaN),0,"invalid saved setting");
        check(!UpgradeUiMath.onTrack(0,0,38),"center text is not a drag target");
        check(UpgradeUiMath.onTrack(45,0,38),"outer arrow is draggable");
        check(!UpgradeUiMath.onTrack(80,0,38),"item cards are not draggable");
        check(UpgradeUiMath.nearestCount(100,1.5,50,64)==3,"1.5x stack");
        check(UpgradeUiMath.nearestCount(100,2,50,64)==4,"2x stack");
        check(UpgradeUiMath.nearestCount(100,5,50,64)==10,"5x stack");
        check(UpgradeUiMath.nearestCount(100,10,50,64)==20,"10x stack");
        check(UpgradeUiMath.nearestCount(100,10,2,64)==64,"max stack is respected");
        check(UpgradeUiMath.nearestCount(100,2,2,16)==0,"impossible stack is not selected");
        check(UpgradeUiMath.nearestCount(100,1.5,200,1)==1,"unstackable reward");
        check(UpgradeUiMath.nearestCount(100,1.5,100,1)==0,"equal-value reward rejected");
        check(UpgradeUiMath.nearestCount(100,1.5,Double.NaN,64)==0,"NaN reward rejected");
        check(UpgradeUiMath.nearestCount(100,1.5,0,64)==0,"zero reward rejected");
        for (double stake:new double[]{.001,1,4,100,10000,1e10})
            for (double multiplier:new double[]{1.5,2,5,10})
                for (double unit:new double[]{.001,.1,1,4,64,10000,1e12})
                    for (int limit:new int[]{1,16,64}) {
                        int count=UpgradeUiMath.nearestCount(stake,multiplier,unit,limit);
                        check(count==0||count<=limit&&count>=1&&unit*count>stake,"valid upgrade stack");
                        if (count>0) {
                            double wanted=stake*multiplier,error=Math.abs(unit*count-wanted);
                            for (int alternative=1;alternative<=limit;alternative++)
                                if (unit*alternative>stake) check(error<=Math.abs(unit*alternative-wanted)+1e-7,"nearest feasible value");
                        }
                    }
        Path directory=Files.createTempDirectory("upgrade-ui-");
        try {
            close(WheelPreferences.load(directory),0,"missing preference default");
            WheelPreferences.save(directory,Math.PI*1.5);
            close(WheelPreferences.load(directory),Math.PI*1.5,"saved angle survives reopening");
            Path setting=directory.resolve("config/upgrade-client.properties");
            Files.writeString(setting,"wheelAngle=garbled\ncustom=preserve\n");
            close(WheelPreferences.load(directory),0,"corrupt preference recovery");
            WheelPreferences.save(directory,-Math.PI/2);
            close(WheelPreferences.load(directory),Math.PI*1.5,"saved normalized angle");
            check(Files.readString(setting).contains("custom=preserve"),"unrelated client settings retained");
            Files.delete(setting); Files.delete(directory.resolve("config"));
        } finally { Files.deleteIfExists(directory); }
        System.out.println("UI MATH PASS: "+checks+" assertions");
    }
}
