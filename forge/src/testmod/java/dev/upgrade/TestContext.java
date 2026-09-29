package dev.upgrade;
import net.minecraft.server.level.ServerLevel;
public record TestContext(ServerLevel getLevel) {
 public void assertTrue(boolean ok,String message) { if (!ok) throw new AssertionError(message); }
 public void succeed() {}
}
