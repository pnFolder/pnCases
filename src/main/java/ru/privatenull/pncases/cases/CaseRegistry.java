package ru.privatenull.pncases.cases;

import org.bukkit.configuration.ConfigurationSection;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.opening.CaseBlockKey;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Loaded cases, their YAML sections and the blocks they are bound to. Accessed from the main thread. */
public final class CaseRegistry {

    private final Map<String, CaseDefinition> casesByName = new HashMap<>();
    private final Map<CaseBlockKey, String> caseByBlock = new HashMap<>();
    private final Map<String, ConfigurationSection> caseSections = new HashMap<>();

    public CaseDefinition byName(String name) {
        return name == null ? null : casesByName.get(name.toLowerCase(Locale.ROOT));
    }

    public ConfigurationSection section(String name) {
        return name == null ? null : caseSections.get(name.toLowerCase(Locale.ROOT));
    }

    /** Name of the case bound to {@code block}, or null. */
    public String ownerOf(CaseBlockKey block) {
        return caseByBlock.get(block);
    }

    /**
     * Whether {@code block} may be the stage of an opening of {@code caseName}: the case's own block, or the
     * block of a bundle case that lists {@code caseName} inside it.
     */
    public boolean serves(CaseBlockKey block, String caseName) {
        if (block == null || caseName == null) return false;
        String owner = caseByBlock.get(block);
        if (owner == null) return false;
        if (owner.equals(caseName)) return true;
        CaseDefinition parent = casesByName.get(owner);
        return parent != null && parent.children().contains(caseName.toLowerCase(Locale.ROOT));
    }

    public CaseDefinition byBlock(CaseBlockKey block) {
        String name = caseByBlock.get(block);
        return name == null ? null : casesByName.get(name);
    }

    public boolean contains(String caseId) {
        return caseId != null && casesByName.containsKey(caseId.toLowerCase(Locale.ROOT));
    }

    public Collection<CaseDefinition> all() {
        return casesByName.values();
    }

    public List<CaseDefinition> sorted() {
        return casesByName.values().stream().sorted(Comparator.comparing(CaseDefinition::name)).toList();
    }

    public Set<String> names() {
        return casesByName.keySet();
    }

    public Set<String> sortedNames() {
        return new TreeSet<>(casesByName.keySet());
    }

    public int size() {
        return casesByName.size();
    }

    public int blockCount() {
        return caseByBlock.size();
    }

    /** Snapshot used to validate a block ownership change before it is applied. */
    public Map<CaseBlockKey, String> blocks() {
        return new HashMap<>(caseByBlock);
    }

    public void replaceAll(Map<String, CaseDefinition> cases, Map<CaseBlockKey, String> blocks,
                           Map<String, ConfigurationSection> sections) {
        casesByName.clear();
        casesByName.putAll(cases);
        caseByBlock.clear();
        caseByBlock.putAll(blocks);
        caseSections.clear();
        caseSections.putAll(sections);
    }

    public void replaceOne(CaseDefinition updated, ConfigurationSection section, Map<CaseBlockKey, String> blocks) {
        caseByBlock.clear();
        caseByBlock.putAll(blocks);
        casesByName.put(updated.name(), updated);
        caseSections.put(updated.name(), section);
    }

    public void clear() {
        casesByName.clear();
        caseByBlock.clear();
        caseSections.clear();
    }

    /** Returns the block map after moving {@code caseName} from {@code previous} to {@code updated}, or null on conflict. */
    static Map<CaseBlockKey, String> replaceOwnedBlocks(
            Map<CaseBlockKey, String> current,
            String caseName,
            Collection<CaseBlockKey> previous,
            Collection<CaseBlockKey> updated
    ) {
        Map<CaseBlockKey, String> candidate = new HashMap<>(current);
        for (CaseBlockKey block : previous) candidate.remove(block, caseName);
        for (CaseBlockKey block : updated) {
            String owner = candidate.putIfAbsent(block, caseName);
            if (owner != null && !owner.equals(caseName)) return null;
        }
        return candidate;
    }
}
