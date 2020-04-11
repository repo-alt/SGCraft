//------------------------------------------------------------------------------------------------
//
//   SG Craft - Computercraft method call queue
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.cc;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

public class CCMethodQueue {

    public static CCMethodQueue instance;

    final Queue<CCCall> items = new ConcurrentLinkedQueue<>();

    public static void init() {
        instance = new CCMethodQueue();
    }

    public static void onServerTick() {
        if (instance != null)
            instance.tick();
    }

    // Called from the Computercraft thread when a method is called from Lua.
    // Blocks until the result is available or an exception occurs.
    public Object[] invoke(IComputerAccess cpu, ILuaContext ctx, Object target, CCMethod method, Object[] args)
            throws LuaException, InterruptedException {
        //System.out.printf("CCMethodQueue.tick: Queueing %s from %s\n", method,
        //  Thread.currentThread().getName());
        CCCall item = new CCCall(cpu, ctx, target, method, args);
        items.add(item);
        item.lock.acquire();
        if (item.exception == null)
            return item.result;
        else
            throw item.exception;
    }

    // Called regularly from the main Minecraft thread to process pending calls.
    void tick() {
        int n = items.size();
        //System.out.printf("CCMethodQueue.tick from %s: %s items in queue\n",
        //  Thread.currentThread().getName(), n);
        while (n-- > 0) {
            CCCall item = items.poll();
            if (item == null)
                return;
            try {
                //System.out.printf("CCMethodQueue.tick: Invoking %s\n", item.method);
                item.result = item.method.invoke(item.cpu, item.ctx, item.target, item.args);
            } catch (LuaException e) {
                item.exception = e;
            } catch (Exception e) {
                item.exception = new LuaException(e.getMessage());
            }
            item.lock.release();
        }
    }

}

class CCCall {

    final IComputerAccess cpu;
    final ILuaContext ctx;
    final Object target;
    final CCMethod method;
    final Object[] args;

    final Semaphore lock = new Semaphore(0);
    Object[] result;
    LuaException exception;

    CCCall(IComputerAccess cpu, ILuaContext ctx, Object target, CCMethod method, Object[] args) {
        this.cpu = cpu;
        this.ctx = ctx;
        this.target = target;
        this.method = method;
        this.args = args;
    }

}
