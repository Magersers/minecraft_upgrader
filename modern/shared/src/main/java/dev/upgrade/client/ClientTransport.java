package dev.upgrade.client;
import dev.upgrade.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
public final class ClientTransport {
    public static void init() { ClientPlayNetworking.registerGlobalReceiver(Payloads.Reply.TYPE,(p,c) -> ClientEvents.receive(p.message())); }
    public static void send(Object packet) {
        if (!ClientPlayNetworking.canSend(Payloads.Request.TYPE)) { ClientEvents.unavailable(); return; }
        ClientPlayNetworking.send(new Payloads.Request(packet));
    }
}
