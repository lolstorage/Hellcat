package me.ht9.rose.feature.module.modules.dupes;

import me.ht9.rose.event.bus.annotation.SubscribeEvent;
import me.ht9.rose.event.events.PacketEvent;
import me.ht9.rose.event.events.TickEvent;
import me.ht9.rose.feature.module.Module;
import me.ht9.rose.feature.module.annotation.Description;
import net.minecraft.src.*;
import org.lwjgl.input.Mouse;

import java.util.List;

@Description("Locks GUI via a button injected directly without Mixins.")
public final class ChestDupe extends Module {
    private static final ChestDupe instance = new ChestDupe();
    private boolean isGuiLocked = false;
    private final int BUTTON_ID = 2999; // Unique ID to track our button

    @Override
    public void onEnable() {
        isGuiLocked = false;
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage("§c[Hellcat] §7ChestDupe active. Injected UI listener.");
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (mc.thePlayer == null || !this.enabled()) return;

        // Automatically toggle off if the screen goes null
        if (mc.currentScreen == null) {
            if (isGuiLocked) {
                isGuiLocked = false;
            }
            return;
        }

        // Dynamically inject the button if the current screen is a chest container
        if (mc.currentScreen instanceof GuiChest) {
            GuiChest chestScreen = (GuiChest) mc.currentScreen;
            
            // Access the controlList from the current screen
            // Note: In your Barn mappings, 'controlList' might be named differently (e.g., 'buttons' or a raw obfuscated field like 'e')
            List controlList = chestScreen.controlList; 

            // Check if our button is already added so we don't spam duplicate entries
            boolean buttonExists = false;
            for (Object obj : controlList) {
                if (obj instanceof GuiButton && ((GuiButton) obj).id == BUTTON_ID) {
                    buttonExists = true;
                    break;
                }
            }

            // If it doesn't exist yet, calculate position and add it to the active screen
            if (!buttonExists) {
                int buttonWidth = 40;
                int buttonHeight = 20;
                
                // Position formulas relative to the chest window boundary
                int x = (chestScreen.width / 2) + (176 / 2) - buttonWidth;
                int y = (chestScreen.height / 2) - (166 / 2) + 5;

                controlList.add(new GuiButton(BUTTON_ID, x, y, buttonWidth, buttonHeight, "Dupe"));
            }

            // --- REPLACEMENT FOR CAPTURING THE CLICK EVENT ---
            // Since we aren't using action handlers via Mixin, we check for manual mouse clicks over the button space
            if (Mouse.isButtonDown(0)) { // Left-click down
                int mouseX = Mouse.getX() * chestScreen.width / mc.displayWidth;
                int mouseY = chestScreen.height - Mouse.getY() * chestScreen.height / mc.displayHeight - 1;

                for (Object obj : controlList) {
                    if (obj instanceof GuiButton) {
                        GuiButton btn = (GuiButton) obj;
                        // Check if the mouse click path intercepted our button dimensions
                        if (btn.id == BUTTON_ID && btn.mousePressed(mc, mouseX, mouseY) && !isGuiLocked) {
                            mc.sndManager.playSoundFX("random.click", 1.0F, 1.0F); // Play vanilla click sound
                            executeDupe();
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
                // Break chest server-side
                mc.getSendQueue().addToSendQueue(new Packet14BlockDig(0, x, y, z, mop.sideHit));
                mc.getSendQueue().addToSendQueue(new Packet14BlockDig(2, x, y, z, mop.sideHit));

                // Force queue flush via Chat Packet
                mc.getSendQueue().addToSendQueue(new Packet3Chat("."));

                isGuiLocked = true;
                mc.thePlayer.addChatMessage("§c[Hellcat] §aQueue Flushed! Window Locked. Click items.");
            }
        }
    }

    @SubscribeEvent
    public void onPacket(PacketEvent event) {
        if (!event.serverBound() && event.packet() instanceof Packet101CloseWindow) {
            if (this.enabled() && isGuiLocked) {
                event.setCancelled(true);
            }
        }
    }

    public static ChestDupe instance() { return instance; }
}
