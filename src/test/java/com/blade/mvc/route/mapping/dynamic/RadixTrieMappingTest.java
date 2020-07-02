package com.blade.mvc.route.mapping.dynamic;

import com.blade.mvc.http.HttpMethod;
import com.blade.mvc.route.Route;
import org.junit.Test;
import com.blade.mvc.route.mapping.dynamic.RadixTrieMapping.Node;

import static org.junit.Assert.*;

/**
 * @author: dqyuan
 * @date: 2020/07/02
 */
public class RadixTrieMappingTest {

    private Route mockRoute(HttpMethod method, String originalPath) {
        return new Route(
                method, originalPath,
                null, null, null, null
        );
    }

    private RadixTrieMapping.Node selectNode(Node partRoot, String name) {
        if (partRoot != null && partRoot.getChildren() != null) {
            for (Node child : partRoot.getChildren()) {
                if (child.getPart().equals(name)) {
                    return child;
                } else {
                    Node res = selectNode(child, name);
                    if (res != null) {
                        return res;
                    }
                }
            }
        }

        return null;
    }

    @Test
    public void testRegisterNormalPaths() {
        RadixTrieMapping radixTrieMapping = new RadixTrieMapping();
        radixTrieMapping.addRoute(HttpMethod.GET, mockRoute(HttpMethod.GET, "/aaa/bbb/ccc"),
                null);
        radixTrieMapping.addRoute(HttpMethod.POST, mockRoute(HttpMethod.GET, "/aaa/bbb/ccc"),
                null);
        radixTrieMapping.addRoute(HttpMethod.GET, mockRoute(HttpMethod.GET, "/aaa/bcb/ccc"),
                null);
        radixTrieMapping.addRoute(HttpMethod.GET, mockRoute(HttpMethod.GET, "/aaa/bcb/ccd"),
                null);
        radixTrieMapping.register();

        assertArrayEquals(new char[] {'b', 'c'}, radixTrieMapping.root.getIndices());
        assertNotNull(radixTrieMapping.root.getChildren()[0].getSupport(HttpMethod.GET));
        assertNotNull(radixTrieMapping.root.getChildren()[0].getSupport(HttpMethod.POST));
        assertNull(radixTrieMapping.root.getChildren()[0].getSupport(HttpMethod.PUT));
    }

    @Test
    public void testRegisterWildPaths() {
        RadixTrieMapping radixTrieMapping = new RadixTrieMapping();
        radixTrieMapping.addRoute(HttpMethod.GET, mockRoute(HttpMethod.GET, "/aaa/*/ccc"),
                null);
        radixTrieMapping.addRoute(HttpMethod.GET, mockRoute(HttpMethod.GET, "/aac/bcb/ccc"),
                null);
        radixTrieMapping.addRoute(HttpMethod.GET, mockRoute(HttpMethod.GET, "/aac/bcb/ccc/*"),
                null);
        radixTrieMapping.addRoute(HttpMethod.GET, mockRoute(HttpMethod.GET, "/aac/fg/:pp/*"),
                null);
        radixTrieMapping.addRoute(HttpMethod.GET, mockRoute(HttpMethod.GET, "/aac/ggg/ll/:eoi"),
                null);
        radixTrieMapping.addRoute(HttpMethod.GET, mockRoute(HttpMethod.GET, "/aac/ggg/ee/:ep/*"),
                null);
        radixTrieMapping.register();

        Node eoiParam = selectNode(radixTrieMapping.root, ":eoi");
        assertNotNull(eoiParam);
        assertNull(eoiParam.getChildren());
    }

    @Test(expected = RuntimeException.class)
    public void testRegisterConflict() {
        RadixTrieMapping radixTrieMapping = new RadixTrieMapping();
        radixTrieMapping.addRoute(HttpMethod.GET, mockRoute(HttpMethod.GET, "/aaa/*"),
                null);
        radixTrieMapping.addRoute(HttpMethod.GET, mockRoute(HttpMethod.GET, "/aaa/:pp"),
                null);
        radixTrieMapping.register();
    }

    @Test
    public void testParamDifferentSuffix() {
        RadixTrieMapping radixTrieMapping = new RadixTrieMapping();
        radixTrieMapping.addRoute(HttpMethod.GET, mockRoute(HttpMethod.GET, "/adm/yonghu/:yonghuId/yonghu"),
                null);
        radixTrieMapping.addRoute(HttpMethod.GET, mockRoute(HttpMethod.GET, "/adm/yonghu/:yonghuId/bill"),
                null);
        radixTrieMapping.register();
    }

    @Test
    public void testParamDifferentSuffix2() {
        RadixTrieMapping radixTrieMapping = new RadixTrieMapping();
        radixTrieMapping.addRoute(HttpMethod.GET, mockRoute(HttpMethod.GET, "/adm/yonghu/:yonghuId/yonghu"),
                null);
        radixTrieMapping.addRoute(HttpMethod.GET, mockRoute(HttpMethod.GET, "/adm/yonghu/:yonghuId"),
                null);
        radixTrieMapping.register();


        Node yonghuIdParam = selectNode(radixTrieMapping.root, ":yonghuId");
        assertNotNull(yonghuIdParam);
        assertEquals('/', yonghuIdParam.getIndices()[0]);
        assertTrue(yonghuIdParam.getRouteMap().containsKey(HttpMethod.GET));
    }

}