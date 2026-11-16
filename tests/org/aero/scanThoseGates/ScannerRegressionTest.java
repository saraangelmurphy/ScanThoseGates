package org.aero.scanThoseGates;

import java.lang.reflect.*;
import java.util.*;
import com.fs.starfarer.api.*;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.campaign.comm.*;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.GateEntityPlugin;
import com.fs.starfarer.api.impl.campaign.abilities.BaseDurationAbility;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.misc.GateIntel;
import com.fs.starfarer.api.loading.AbilitySpecAPI;
import org.aero.scanThoseGates.campaign.abilities.*;
import org.aero.scanThoseGates.campaign.listeners.RelocationListener;
import org.aero.scanThoseGates.misc.Utilities;

/** Runs actual scanner classes and ability base classes with proxy campaign services. */
public class ScannerRegressionTest {
    interface Call { Object invoke(Method method, Object[] args) throws Throwable; }
    static int checks;
    static boolean hidden;
    static final Map<String,Object> sectorMemory = new HashMap<>(), gateMemory = new HashMap<>();
    static final List<SectorEntityToken> gates = new ArrayList<>();
    static final List<IntelInfoPlugin> intel = new ArrayList<>();
    static final List<SectorEntityToken> shunts = new ArrayList<>(), sleepers = new ArrayList<>();
    static final Set<String> characterAbilities = new HashSet<>();
    static final List<Object> listeners = new ArrayList<>();
    static StarSystemAPI system;
    static SectorEntityToken gate;
    @SuppressWarnings("unchecked") static <T> T proxy(Class<T> type, Call call) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p,m,a)-> {
            if(m.getName().equals("toString")) return type.getSimpleName();
            if(m.getName().equals("hashCode")) return System.identityHashCode(p);
            if(m.getName().equals("equals")) return p==a[0];
            Object r=call.invoke(m,a==null?new Object[0]:a);
            if(r!=null || !m.getReturnType().isPrimitive()) return r;
            if(m.getReturnType()==boolean.class) return false;
            if(m.getReturnType()==float.class) return 0f;
            if(m.getReturnType()==int.class) return 0;
            return null;
        });
    }
    static MemoryAPI memory(Map<String,Object> map) {
        return proxy(MemoryAPI.class,(m,a)->switch(m.getName()) {
            case "getBoolean" -> Boolean.TRUE.equals(map.get(a[0]));
            case "set" -> { map.put((String)a[0],a[1]); yield null; }
            case "get" -> map.get(a[0]);
            case "contains" -> map.containsKey(a[0]);
            default -> null;
        });
    }
    static void check(boolean b,String message) { if(!b) throw new AssertionError(message); checks++; }
    static void invoke(Object obj,String name,Class<?>[] types,Object... args) throws Exception {
        Method m=obj.getClass().getDeclaredMethod(name,types); m.setAccessible(true); m.invoke(obj,args);
    }
    static void checkGates(GateScanner scanner) throws Exception { invoke(scanner,"checkForGates",new Class<?>[0]); }
    static void setup() {
        AbilitySpecAPI spec=proxy(AbilitySpecAPI.class,(m,a)->switch(m.getName()) {
            case "getActivationDays", "getDurationDays", "getDeactivationDays", "getDeactivationCooldown" -> 1f;
            default -> null;
        });
        ModManagerAPI mods=proxy(ModManagerAPI.class,(m,a)->false);
        Global.setSettings(proxy(SettingsAPI.class,(m,a)->switch(m.getName()) {
            case "getModManager" -> mods;
            case "getAbilitySpec" -> spec;
            default -> null;
        }));
        MarketAPI market=proxy(MarketAPI.class,(m,a)->m.getName().equals("isHidden")?hidden:null);
        system=proxy(StarSystemAPI.class,(m,a)->switch(m.getName()) {
            case "getName", "getNameWithLowercaseType" -> "Test system";
            case "getEntitiesWithTag" -> List.of();
            default -> null;
        });
        gate=proxy(SectorEntityToken.class,(m,a)->switch(m.getName()) {
            case "getMemoryWithoutUpdate", "getMemory" -> memory(gateMemory);
            case "getContainingLocation" -> system;
            case "getName" -> "Test gate";
            default -> null;
        });
        EconomyAPI economy=proxy(EconomyAPI.class,(m,a)->switch(m.getName()) {
            case "getStarSystemsWithMarkets" -> List.of(system);
            case "getMarkets" -> List.of(market);
            default -> null;
        });
        IntelManagerAPI manager=proxy(IntelManagerAPI.class,(m,a)->switch(m.getName()) {
            case "getIntel" -> a.length==0?intel:intel.stream().filter(((Class<?>)a[0])::isInstance).toList();
            case "addIntel" -> { intel.add((IntelInfoPlugin)a[0]); yield null; }
            default -> null;
        });
        CharacterDataAPI character=proxy(CharacterDataAPI.class,(m,a)->switch(m.getName()) {
            case "getAbilities" -> characterAbilities;
            case "addAbility" -> { characterAbilities.add((String)a[0]); yield null; }
            default -> null;
        });
        MutableCharacterStatsAPI stats=proxy(MutableCharacterStatsAPI.class,(m,a)->m.getName().equals("getGrantedAbilityIds")?List.of():null);
        PersonAPI person=proxy(PersonAPI.class,(m,a)->m.getName().equals("getStats")?stats:null);
        ListenerManagerAPI listenerManager=proxy(ListenerManagerAPI.class,(m,a)->switch(m.getName()) {
            case "addListener" -> { listeners.add(a[0]); yield null; }
            case "removeListenerOfClass" -> { listeners.removeIf(((Class<?>)a[0])::isInstance); yield null; }
            default -> null;
        });
        Global.setSector(proxy(SectorAPI.class,(m,a)->switch(m.getName()) {
            case "getMemoryWithoutUpdate" -> memory(sectorMemory);
            case "getEconomy" -> economy;
            case "getCustomEntitiesWithTag" -> a[0].equals(Tags.GATE)?gates:a[0].equals(Tags.CORONAL_TAP)?shunts:a[0].equals(Tags.CRYOSLEEPER)?sleepers:List.of();
            case "getIntelManager" -> manager;
            case "getStarSystems" -> List.of();
            case "getCharacterData" -> character;
            case "getPlayerPerson" -> person;
            case "getListenerManager" -> listenerManager;
            default -> null;
        }));
    }
    static void gateChecks() throws Exception {
        GateScanner scanner=new GateScanner();
        gates.add(gate); checkGates(scanner);
        check(Boolean.TRUE.equals(sectorMemory.get(ModPlugin.UNSCANNED_GATES)),"Unscanned gate in visible market system is pending");
        hidden=true; checkGates(scanner);
        check(Boolean.FALSE.equals(sectorMemory.get(ModPlugin.UNSCANNED_GATES)),"Market becoming hidden clears pending gate");
        check(GateScanner.Companion.getSystemsWithMarkets().isEmpty(),"Hidden market does not remain cached");
        ModPlugin.Settings.setActivateAllGates(true); checkGates(scanner);
        check(Boolean.TRUE.equals(sectorMemory.get(ModPlugin.UNSCANNED_GATES)),"Activate-all includes marketless/hidden systems");
        ModPlugin.Settings.setActivateAllGates(false); ModPlugin.Settings.setRevealAllGates(true); checkGates(scanner);
        check(Boolean.TRUE.equals(sectorMemory.get(ModPlugin.UNSCANNED_GATES)),"Reveal-all includes unreported gate");
        ModPlugin.Settings.setRevealAllGates(false); gateMemory.put(GateEntityPlugin.GATE_SCANNED,true); checkGates(scanner);
        check(Boolean.TRUE.equals(sectorMemory.get(ModPlugin.UNSCANNED_GATES)),"Scanned gate missing intel is pending");
        invoke(scanner,"applyEffect",new Class<?>[]{float.class,float.class},1f,1f);
        check(intel.size()==1 && intel.get(0) instanceof GateIntel,"Scan adds intel for already scanned gate");
        checkGates(scanner);
        check(Boolean.FALSE.equals(sectorMemory.get(ModPlugin.UNSCANNED_GATES)),"Reported scanned gate is no longer pending");
        gates.clear(); sectorMemory.put(ModPlugin.UNSCANNED_GATES,true); checkGates(scanner);
        check(Boolean.FALSE.equals(sectorMemory.get(ModPlugin.UNSCANNED_GATES)),"Removing gates clears stale availability");
        sectorMemory.put(GateEntityPlugin.CAN_SCAN_GATES,true); sectorMemory.put(GateEntityPlugin.GATES_ACTIVE,true);
        sectorMemory.put(ModPlugin.UNSCANNED_GATES,true); scanner.init(ModPlugin.GATE_SCAN_ABILITY,gate);
        check(scanner.isUsable(),"Gate ability requires story unlock and available gate");
        scanner.setCooldownLeft(1f); check(!scanner.isUsable(),"Gate ability respects cooldown");
        scanner.setCooldownLeft(0f); scanner.forceDisable(); check(!scanner.isUsable(),"Gate ability respects forced disable");
    }
    static void surveyChecks() throws Exception {
        for(BaseDurationAbility ability:List.of(new CryosleeperScanner(),new HypershuntScanner())) {
            String flag=ability instanceof CryosleeperScanner?ModPlugin.ALLOW_CRYOSLEEPER_SCAN:ModPlugin.ALLOW_HYPERSHUNT_SCAN;
            sectorMemory.put(flag,true); ability.init("test",gate); check(ability.isUsable(),"Unused survey is usable");
            ability.setCooldownLeft(1f); check(!ability.isUsable(),"Survey respects cooldown");
            ability.setCooldownLeft(0f); ability.forceDisable(); check(!ability.isUsable(),"Survey respects forced disable");
            sectorMemory.put(flag,false); check(!ability.isUsable(),"Used survey stays unavailable");
        }
        new RelocationListener().reportCurrentLocationChanged(null,null);
        new RelocationListener().reportCurrentLocationChanged(null,system);
        check(true,"Nullable first-load relocation callbacks are safe");
        SectorEntityToken token=proxy(SectorEntityToken.class,(m,a)->null);
        check(Utilities.getSystemNameOrHyperspace(token).equals("Hyperspace"),"Relocated megastructure location is safe outside systems");
        SectorEntityToken another=proxy(SectorEntityToken.class,(m,a)->null);
        sleepers.addAll(List.of(token,another)); shunts.addAll(List.of(token,another));
        for(BaseDurationAbility ability:List.of(new CryosleeperScanner(),new HypershuntScanner())) {
            String flag=ability instanceof CryosleeperScanner?ModPlugin.ALLOW_CRYOSLEEPER_SCAN:ModPlugin.ALLOW_HYPERSHUNT_SCAN;
            sectorMemory.put(flag,true); int before=intel.size();
            invoke(ability,"applyEffect",new Class<?>[]{float.class,float.class},1f,1f);
            check(intel.size()==before+2,"Survey reports all matching structures, not just the first");
            check(Boolean.FALSE.equals(sectorMemory.get(flag)),"Successful survey becomes one-shot unavailable");
            invoke(ability,"applyEffect",new Class<?>[]{float.class,float.class},1f,1f);
            check(intel.size()==before+2,"Further effect frames do not duplicate structure reports");
        }
    }
    static void versionChecks() {
        check(ModPlugin.Settings.isLunaVersionSupported("1.7.4"),"Exact minimum Luna version accepted");
        check(ModPlugin.Settings.isLunaVersionSupported("2.1.0-RC4"),"Modern prerelease version accepted");
        check(ModPlugin.Settings.isLunaVersionSupported("2.1"),"Two-component modern version accepted");
        check(!ModPlugin.Settings.isLunaVersionSupported("1.7.3"),"Older patch rejected");
        check(!ModPlugin.Settings.isLunaVersionSupported("1.6.99"),"Older minor rejected");
        check(!ModPlugin.Settings.isLunaVersionSupported("invalid"),"Malformed version rejected without exception");
    }
    static void lifecycleChecks() {
        // Mock zero/nonpositive polling intervals must be repaired, not used in a per-frame busy loop.
        ModPlugin.Settings.readSettings();
        check(ModPlugin.Settings.getUsableCheckInterval()==1f,"Invalid polling interval falls back to one second");
        check(ModPlugin.Settings.getUsableCheckReportInterval()==60f,"Invalid diagnostics interval falls back to sixty seconds");
        GateScanner.Companion.getSystemsWithMarkets().add(system);
        sectorMemory.put(ModPlugin.ALLOW_CRYOSLEEPER_SCAN,false);
        ModPlugin plugin=new ModPlugin(); plugin.onGameLoad(false);
        check(GateScanner.Companion.getSystemsWithMarkets().isEmpty(),"Loading another save clears shared market cache");
        check(characterAbilities.size()==3,"All three scanner abilities are installed on save load");
        check(Boolean.FALSE.equals(sectorMemory.get(ModPlugin.ALLOW_CRYOSLEEPER_SCAN)),"Used scan flag survives save load");
        check(listeners.size()==2,"Two transient discovery/relocation listeners are registered");
        plugin.onGameLoad(false);
        check(listeners.size()==2 && characterAbilities.size()==3,"Repeated save loads do not duplicate listeners or abilities");
    }
    public static void main(String[] args) throws Exception {
        try { setup(); gateChecks(); surveyChecks(); versionChecks(); lifecycleChecks(); System.out.println("PASS: "+checks+" scanner regression checks against installed game API"); }
        finally { Global.setSector(null); Global.setSettings(null); }
    }
}
