package me.ht9.rose.feature.module.modules.dupes;

import me.ht9.rose.event.bus.annotation.SubscribeEvent;
import me.ht9.rose.event.events.PacketEvent;
import me.ht9.rose.event.events.TickEvent;
import me.ht9.rose.feature.module.Module;
import me.ht9.rose.feature.module.annotation.Description;
import net.minecraft.src.*;
import org.lwjgl.input.Mouse;

import java.util.List;

@Description("Locks GUI via an injected UI button. Left-click for 0-stack, Right-click for -1.")
public final class ChestDupe extends Module {
    private static final ChestDupe instance = new ChestDupe();
    private boolean isGuiLocked = false;
    private final int BUTTON_ID = 2999; 

    @Override
    public void onEnable() {
        isGuiLocked = false;
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage("§c[Hellcat] §7ChestDupe ready. Open a chest to use the UI button.");
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (mc.thePlayer == null || !this.enabled()) return;

        // Auto-reset state if the interface is closed manually
        if (mc.currentScreen == null) {
            if (isGuiLocked) {
                isGuiLocked = false;
            }
            return;
        }

        // Target the chest interface dynamically
        if (mc.currentScreen instanceof GuiChest) {
            GuiChest chestScreen = (GuiChest) mc.currentScreen;
            List controlList = null;

            // Bypassing the protected visibility rules via Reflection
            try {
                // NOTE: If using production intermediate mappings outside the IDE, 
                // swap "controlList" with the mapped runtime field name if necessary.
                java.lang.reflect.Field field = GuiScreen.class.getDeclaredField("controlList");
                field.setAccessible(true);
                controlList = (List) field.get(chestScreen);
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (controlList != null) {
                boolean buttonExists = false;
                for (Object obj : controlList) {
                    if (obj instanceof GuiButton && ((GuiButton) obj).id == BUTTON_ID) {
                        buttonExists = true;
                        break;
                    }
                }

                // Inject the button into the top right corner relative to the chest window bounds
                if (!buttonExists) {
                    int buttonWidth = 40;
                    int buttonHeight = 20;
                    
                    int x = (chestScreen.width / 2) + (176 / 2) - buttonWidth;
                    int y = (chestScreen.height / 2) - (166 / 2) + 5;

                    controlList.add(new GuiButton(BUTTON_ID, x, y, buttonWidth, buttonHeight, "Dupe"));
                }

                // Manual UI Mouse click detection loop
                if (Mouse.isButtonDown(0)) {
                    int mouseX = Mouse.getX() * chestScreen.width / mc.displayWidth;
                    int mouseY = chestScreen.height - Mouse.getY() * chestScreen.height / mc.displayHeight - 1;

                    for (Object obj : controlList) {
                        if (obj instanceof GuiButton) {
                            GuiButton btn = (GuiButton) obj;
                            if (btn.id == BUTTON_ID && btn.mousePressed(mc, mouseX, mouseY) && !isGuiLocked) {
                                mc.sndManager.playSoundFX("random.click", 1.0F, 1.0F);
                                executeDupe();
                            }
                        }
                    }
                }
            }
        }
    }

    private void executeDupe() {
        if (mc.objectMouseOver == null) return;

        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop.typeOfHit == EnumMovingObjectType.TILE) {
            int x = mop.blockX;
            int y = mop.blockY;
            int z = mop.blockZ;

            if (mc.theWorld.getBlockId(x, y, z) == Block.chest.blockID) {
                // 1. Fire packet sequences to break the block instantly on the server thread
                mc.getSendQueue().addToSendQueue(new Packet14BlockDig(0, x, y, z, mop.sideHit));
                mc.getSendQueue().addToSendQueue(new Packet14BlockDig(2, x, y, z, mop.sideHit));

                // 2. Force network queue flush using a harmless string message packet
                mc.getSendQueue().addToSendQueue(new Packet3Chat("."));

                isGuiLocked = true;
                mc.thePlayer.addChatMessage("§c[Hellcat] §aWindow Locked! Click slots to duplicate.");
            }
        }
    }

    @SubscribeEvent
    public void onPacket(PacketEvent event) {
        // Drop incoming close window notifications to preserve the ghost window context
        if (!event.serverBound() && event.packet() instanceof Packet101CloseWindow) {
            if (this.enabled() && isGuiLocked) {
                event.setCancelled(true);
            }
        }
    }

    public static ChestDupe instance() { return instance; }
}
