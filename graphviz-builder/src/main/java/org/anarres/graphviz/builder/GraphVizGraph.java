/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.anarres.graphviz.builder;

import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.io.ByteSink;
import com.google.common.io.Files;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
// import org.apache.commons.io.output.StringBuilderWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author shevek
 */
public class GraphVizGraph {

    private static final Logger LOG = LoggerFactory.getLogger(GraphVizGraph.class);
    private static final long serialVersionUID = 1L;
    private int counter = 0;
    @CheckForNull
    private GraphVizLabel label;
    private final Predicate<? super GraphVizScope> scopes;
    private transient final Map<GraphVizNode.Key, GraphVizNode> nodes = new HashMap<GraphVizNode.Key, GraphVizNode>();
    private transient final Map<GraphVizEdge.Key, GraphVizEdge> edges = new HashMap<GraphVizEdge.Key, GraphVizEdge>();
    private transient final Map<GraphVizCluster.Key, GraphVizCluster> clusters = new HashMap<GraphVizCluster.Key, GraphVizCluster>();

    public GraphVizGraph(@Nonnull Predicate<? super GraphVizScope> scopes) {
        this.scopes = scopes;
    }

    public GraphVizGraph() {
        this(Predicates.alwaysTrue());
    }

    protected boolean isScopeVisible(@Nonnull GraphVizScope scope) {
        return scopes.apply(scope);
    }

    @CheckForNull
    public GraphVizLabel getLabel() {
        return label;
    }

    @Nonnull
    public GraphVizLabel label() {
        if (label == null)
            label = new GraphVizLabel();
        return label;
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    public GraphVizGraph label(@Nonnull CharSequence csq) {
        label().set(csq);
        return this;
    }

    private void clear(@Nonnull GraphVizScope scope, @Nonnull Iterable<? extends GraphVizObject.Key> data) {
        Iterator<? extends GraphVizObject.Key> it = data.iterator();
        while (it.hasNext()) {
            GraphVizObject.Key key = it.next();
            if (Objects.equal(key.getScope(), scope))
                it.remove();
        }
    }

    public void clear(@Nonnull GraphVizScope scope) {
        clear(scope, clusters.keySet());
        for (GraphVizCluster cluster : clusters.values()) {
            clear(scope, cluster.getClusterKeys());
            clear(scope, cluster.getNodeKeys());
        }

        clear(scope, nodes.keySet());

        Iterator<? extends GraphVizEdge.Key> it = edges.keySet().iterator();
        while (it.hasNext()) {
            GraphVizEdge.Key key = it.next();
            if (Objects.equal(key.getSourceKey().getScope(), scope))
                it.remove();
            else if (Objects.equal(key.getTargetKey().getScope(), scope))
                it.remove();
        }
    }

    @Nonnull
    public GraphVizNode node(@Nonnull GraphVizScope scope, @Nonnull Object object) {
        GraphVizNode.Key key = new GraphVizNode.Key(scope, object);
        if (!isScopeVisible(scope))
            return new GraphVizNode(this, key, -1);
        GraphVizNode node = nodes.get(key);
        if (node == null) {
            int id = counter++;
            // LOG.info("n" + id + " -> " + object, new Exception());
            node = new GraphVizNode(this, key, id);
            nodes.put(key, node);
        }
        return node;
    }

    public boolean containsNode(@Nonnull GraphVizScope scope, @Nonnull Object object) {
        GraphVizNode.Key key = new GraphVizNode.Key(scope, object);
        return nodes.containsKey(key);
    }

    @Nonnull
    public GraphVizEdge edge(@Nonnull GraphVizObject<?> source, @Nonnull GraphVizObject<?> target) {
        GraphVizEdge.Key key = new GraphVizEdge.Key(source.getKey(), target.getKey());
        if (!isScopeVisible(source.getScope()))
            return new GraphVizEdge(this, key, "", "");
        if (!isScopeVisible(target.getScope()))
            return new GraphVizEdge(this, key, "", "");
        GraphVizEdge edge = edges.get(key);
        if (edge == null) {
            edge = new GraphVizEdge(this, key, source.getId(), target.getId());
            edges.put(key, edge);
            if (source instanceof GraphVizCluster)
                edge.logicalTail(source.getId());
            if (target instanceof GraphVizCluster)
                edge.logicalHead(target.getId());
        }
        return edge;
    }

    @Nonnull
    public GraphVizEdge edge(@Nonnull GraphVizScope scope, @Nonnull Object source, @Nonnull Object target) {
        return edge(node(scope, source), node(scope, target));
    }

    public boolean containsEdge(@Nonnull GraphVizObject<?> source, @Nonnull GraphVizObject<?> target) {
        GraphVizEdge.Key key = new GraphVizEdge.Key(source.getKey(), target.getKey());
        return edges.containsKey(key);
    }

    public boolean containsEdge(@Nonnull GraphVizScope scope, @Nonnull Object source, @Nonnull Object target) {
        GraphVizObject.Key k_source = new GraphVizObject.Key(scope, source);
        GraphVizObject.Key k_target = new GraphVizObject.Key(scope, target);
        GraphVizEdge.Key key = new GraphVizEdge.Key(k_source, k_target);
        return edges.containsKey(key);
    }

    @Nonnull
    private GraphVizCluster cluster(@CheckForNull GraphVizCluster parent, @Nonnull GraphVizScope scope, @Nonnull Object object) {
        GraphVizCluster.Key key = new GraphVizCluster.Key(scope, object);
        if (!isScopeVisible(scope))
            return new GraphVizCluster(this, key, null, -1);
        GraphVizCluster cluster = clusters.get(key);
        if (cluster == null) {
            cluster = new GraphVizCluster(this, key, parent, counter++);
            clusters.put(key, cluster);
        }
        return cluster;
    }

    @Nonnull
    public GraphVizCluster cluster(@Nonnull GraphVizScope scope, @Nonnull Object object) {
        return cluster(null, scope, object);
    }

    @Nonnull
    public GraphVizCluster subcluster(@Nonnull GraphVizCluster parent, @Nonnull Object object) {
        return cluster(parent, parent.getScope(), object);
    }

    private static boolean append(@Nonnull Appendable writer, @Nonnull String key, @CheckForNull Object value, boolean quote, boolean first) throws IOException {
        if (value == null)
            return first;
        if (!first)
            writer.append(',');
        writer.append(key).append('=');
        if (quote)
            writer.append('"');
        writer.append(String.valueOf(value));
        if (quote)
            writer.append('"');
        return false;
    }

    private static void writeIndent(@Nonnull Writer writer, @Nonnegative int depth) throws IOException {
        for (int i = 0; i < depth; i++)
            writer.write('\t');
    }

    private void writeTo(
            @Nonnull Writer writer,
            @Nonnull Map<? extends GraphVizCluster, ? extends Iterable<? extends GraphVizCluster>> clusterMap,
            @CheckForNull GraphVizCluster parent,
            @Nonnegative int depth) throws IOException {
        Iterable<? extends GraphVizCluster> _clusters = clusterMap.remove(parent);
        if (_clusters == null)
            return;
        for (GraphVizCluster cluster : _clusters) {
            writeIndent(writer, depth);
            writer.append("\tsubgraph ").append(cluster.getId()).append(" {\n");
            writeIndent(writer, depth);
            writer.append("\t\t// scope=").append(String.valueOf(System.identityHashCode(cluster.getKey().getScope()))).append("\n");
            writeIndent(writer, depth);
            writer.append("\t\t").append(cluster.getId()).append(" [label=\"\",shape=point,style=invis];\n");
            if (cluster.getLabel() != null) {
                writeIndent(writer, depth);
                writer.append("\t\t").append("label=\"").append(String.valueOf(cluster.getLabel())).append("\";\n");
            }
            for (GraphVizNode.Key key : cluster.getNodeKeys()) {
                GraphVizNode node = node(key.getScope(), key.getObject());
                writeIndent(writer, depth);
                writer.append("\t\t").append(node.getId()).append(";\n");
            }
            writeTo(writer, clusterMap, cluster, depth + 1);
            writeIndent(writer, depth);
            writer.append("\t}\n");
        }
    }

    /** This map allows null keys, and the null key should be present. */
    @Nonnull
    private Map<GraphVizCluster, List<GraphVizCluster>> newClusterMap() {
        Map<GraphVizCluster, List<GraphVizCluster>> clusterMap = new HashMap<GraphVizCluster, List<GraphVizCluster>>();
        for (GraphVizCluster child : clusters.values()) {
            @CheckForNull
            GraphVizCluster parent = child.getParent();
            List<GraphVizCluster> children = clusterMap.get(parent);
            if (children == null) {
                children = new ArrayList<GraphVizCluster>();
                clusterMap.put(parent, children);
            }
            children.add(child);
        }
        return clusterMap;
    }

    public void writeTo(@Nonnull Writer out) throws IOException {
        Writer writer;
        if (out instanceof BufferedWriter)
            writer = out;
        else if (out instanceof StringWriter)
            writer = out;
        // else if (out instanceof StringBuilderWriter) writer = out;
        else
            writer = new BufferedWriter(out);
        writer.write("digraph G {\n");
        writer.write("\tcompound=true;\n");
        // writer.write("\tranksep=1.5;\n");
        writer.write("\tnode [shape=box];\n");
        if (getLabel() != null) {
            writer.append("\t");
            append(writer, "label", getLabel(), true, true);
            writer.append(";\n");
        }

        for (GraphVizNode node : nodes.values()) {
            writer.append("\t").append(node.getId()).append(" [");
            boolean first = true;
            first = append(writer, "color", node.getColor(), true, first);
            first = append(writer, "style", node.getStyle(), true, first);
            first = append(writer, "shape", node.getShape(), true, first);
            first = append(writer, "label", node.getLabel(), true, first);
            writer.append("];\n");
        }

        for (Map.Entry<GraphVizEdge.Key, GraphVizEdge> e : edges.entrySet()) {
            // GraphVizEdge.Key key = e.getKey();
            GraphVizEdge edge = e.getValue();

            writer.append("\t").append(edge.getSourceId()).append(" -> ").append(edge.getTargetId()).append(" [");
            boolean first = true;
            first = append(writer, "color", edge.getColor(), true, first);
            first = append(writer, "style", edge.getStyle(), true, first);
            first = append(writer, "arrowhead", edge.getHeadShape(), true, first);
            first = append(writer, "arrowtail", edge.getTailShape(), true, first);
            first = append(writer, "label", edge.getLabel(), true, first);
            first = append(writer, "headlabel", edge.getHeadLabel(), true, first);
            first = append(writer, "taillabel", edge.getTailLabel(), true, first);
            first = append(writer, "lhead", edge.getLogicalHead(), false, first);
            first = append(writer, "ltail", edge.getLogicalTail(), false, first);
            writer.append("];\n");
        }

        /*
         for (GraphVizCluster cluster : clusters.values()) {
         writer.append("\tsubgraph ").append(cluster.getId()).append(" {\n");
         if (cluster.getLabel() != null)
         writer.append("\t\t").append("label=\"").append(String.valueOf(cluster.getLabel())).append("\";\n");
         for (GraphVizNode.Key key : cluster.getNodeKeys()) {
         GraphVizNode node = node(key.getScope(), key.getObject());
         writer.append("\t\t").append(node.getId()).append(";\n");
         }
         writer.append("\t}\n");
         }
         */
        Map<GraphVizCluster, List<GraphVizCluster>> clusterMap = newClusterMap();
        writeTo(writer, clusterMap, null, 0);
        writer.write("}\n");
        writer.flush();
    }

    public void writeTo(@Nonnull OutputStream out) throws IOException {
        writeTo(new OutputStreamWriter(out, Charsets.UTF_8));
    }

    public void writeTo(@Nonnull File file) throws IOException {
        ByteSink sink = Files.asByteSink(file);
        OutputStream out = sink.openBufferedStream();
        try {
            writeTo(out);
        } finally {
            out.close();
        }
    }

    /*
     @Override
     public String toString() {
     StringBuilderWriter writer = new StringBuilderWriter();
     try {
     writeTo(writer);
     } catch (IOException ex) {
     PrintWriter pw = new PrintWriter(writer);
     ex.printStackTrace(pw);
     pw.close();
     }
     return writer.toString();
     }
     */
    @Override
    public String toString() {
        return "GraphVizGraph(" + nodes.size() + " nodes, " + edges.size() + " edges)";
    }
}
