package apoc.export.json;

import apoc.ApocSettings;
import apoc.graph.Graphs;
import apoc.util.TestUtil;
import org.junit.*;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.spatial.Point;
import org.neo4j.test.rule.DbmsRule;
import org.neo4j.test.rule.ImpermanentDbmsRule;
import org.neo4j.values.storable.CoordinateReferenceSystem;
import org.neo4j.values.storable.DurationValue;
import org.neo4j.values.storable.PointValue;
import org.neo4j.values.storable.Values;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Map;

import static apoc.util.MapUtil.map;

public class ImportJsonTest {

    private static File directory = new File("../docs/asciidoc/data/exportJSON");

    @Rule
    public DbmsRule db = new ImpermanentDbmsRule()
            .withSetting(GraphDatabaseSettings.load_csv_file_url_root, directory.toPath().toAbsolutePath())
            .withSetting(ApocSettings.apoc_import_file_enabled, true);

    @Before
    public void setUp() throws Exception {
        TestUtil.registerProcedure(db, ImportJson.class);
    }

    @Test
    public void shouldImportAllJson() throws Exception {
        // given
        String filename = "all.json";

        // when
        TestUtil.testCall(db, "CALL apoc.import.json($file, null)",
                map("file", filename),
                (r) -> {
                    // then
                    Assert.assertEquals("all.json", r.get("file"));
                    Assert.assertEquals("file", r.get("source"));
                    Assert.assertEquals("json", r.get("format"));
                    Assert.assertEquals(3L, r.get("nodes"));
                    Assert.assertEquals(1L, r.get("relationships"));
                    Assert.assertEquals(15L, r.get("properties"));
                    Assert.assertEquals(4L, r.get("rows"));
                    Assert.assertEquals(true, r.get("done"));

                    try(Transaction tx = db.beginTx()) {
                        final long countNodes = tx.execute("MATCH (n:User) RETURN count(n) AS count")
                                .<Long>columnAs("count")
                                .next();
                        Assert.assertEquals(3L, countNodes);

                        final long countRels = tx.execute("MATCH ()-[r:KNOWS]->() RETURN count(r) AS count")
                                .<Long>columnAs("count")
                                .next();
                        Assert.assertEquals(1L, countRels);

                        final Map<String, Object> props = tx.execute("MATCH (n:User {name: 'Adam'}) RETURN n")
                                .<Node>columnAs("n")
                                .next()
                                .getAllProperties();

                        Assert.assertEquals(9, props.size());
                        Assert.assertEquals("wgs-84", props.get("place.crs"));
                        Assert.assertEquals(33.46789D, props.get("place.latitude"));
                        Assert.assertEquals(13.1D, props.get("place.longitude"));
                        Assert.assertFalse(props.containsKey("place"));
                    }
                }
        );
    }

    @Test
    public void shouldImportAllJsonWithPropertyMappings() throws Exception {
        // given
        String filename = "all.json";

        // when
        TestUtil.testCall(db, "CALL apoc.import.json($file, $config)",
                map("file", filename, "config",
                        map("nodePropertyMappings", map("User", map("place", "Point", "born", "LocalDateTime")),
                        "relPropertyMappings", map("KNOWS", map("bffSince", "Duration"))), "unwindBatchSize", 1, "txBatchSize", 1),
                (r) -> {
                    // then
                    Assert.assertEquals("all.json", r.get("file"));
                    Assert.assertEquals("file", r.get("source"));
                    Assert.assertEquals("json", r.get("format"));
                    Assert.assertEquals(3L, r.get("nodes"));
                    Assert.assertEquals(1L, r.get("relationships"));
                    Assert.assertEquals(15L, r.get("properties"));
                    Assert.assertEquals(4L, r.get("rows"));
                    Assert.assertEquals(true, r.get("done"));

                    try(Transaction tx = db.beginTx()) {
                        final long countNodes = tx.execute("MATCH (n:User) RETURN count(n) AS count")
                                .<Long>columnAs("count")
                                .next();
                        Assert.assertEquals(3L, countNodes);

                        final long countRels = tx.execute("MATCH ()-[r:KNOWS]->() RETURN count(r) AS count")
                                .<Long>columnAs("count")
                                .next();
                        Assert.assertEquals(1L, countRels);

                        final Map<String, Object> props = tx.execute("MATCH (n:User {name: 'Adam'}) RETURN n")
                                .<Node>columnAs("n")
                                .next()
                                .getAllProperties();
                        Assert.assertEquals(7, props.size());
                        Assert.assertTrue(props.get("place") instanceof PointValue);
                        PointValue point = (PointValue) props.get("place");
                        final PointValue pointValue = Values.pointValue(CoordinateReferenceSystem.WGS84, 33.46789D, 13.1D);
                        Assert.assertTrue(point.equals((Point) pointValue));
                        Assert.assertTrue(props.get("born") instanceof LocalDateTime);

                        Relationship rel = tx.execute("MATCH ()-[r:KNOWS]->() RETURN r")
                                .<Relationship>columnAs("r")
                                .next();
                        Assert.assertTrue(rel.getProperty("bffSince") instanceof DurationValue);
                        Assert.assertEquals("P5M1DT12H", rel.getProperty("bffSince").toString());
                    }
                }
        );
    }
}
