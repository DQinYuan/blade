package com.blade.mvc.route.mapping.dynamic;

import com.blade.mvc.http.HttpMethod;
import com.blade.mvc.route.DynamicMapping;
import com.blade.mvc.route.Route;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author: dqyuan
 * @date: 2020/06/27
 */
public class RadixTrieMapping implements DynamicMapping {

    private final MethodAndRoutes registerCandidate = new MethodAndRoutes(0);

    protected Node root = new Node();

    private static MethodAndRoutes[] groupBy(List<MethodAndRoute> methodAndRoutes, int pos) {
        Map<Character, MethodAndRoutes> groupMap = new HashMap<>();
        for (MethodAndRoute methodAndRoute : methodAndRoutes) {
            char pivot = methodAndRoute.getPath().charAt(pos);
            groupMap.computeIfAbsent(pivot, ignore -> new MethodAndRoutes(pos))
                    .add(methodAndRoute);
        }

        // conflict
        if (groupMap.containsKey('*') || groupMap.containsKey(':')) {
            Map<Boolean, List<Character>> isWildRoutes = groupMap.keySet()
                    .stream().collect(Collectors.partitioningBy(k -> k == '*' || k == ':'));
            throw new RuntimeException(
                    String.format("url %s conflict with %s",
                            groupMap.get(isWildRoutes.get(true).get(0)).get(0).getPath(),
                            groupMap.get(isWildRoutes.get(false).get(0)).get(0).getPath()
                    )
            );
        }

        return groupMap.values().toArray(new MethodAndRoutes[0]);
    }

    private static class MethodAndRoutes extends ArrayList<MethodAndRoute> {

        private int minLength = Integer.MAX_VALUE;
        private String minLenUrl = "";

        private String maxLenUrl = "";
        private int maxLength = Integer.MIN_VALUE;

        private int offset;

        enum MatchStatus {
            START(true), SLASH(true), WILD(false), PARAM(false);

            private final boolean canSplit;

            MatchStatus(boolean canSplit) {
                this.canSplit = canSplit;
            }
        }

        private MethodAndRoutes(int offset) {
            this.offset = offset;
        }

        @Override
        public boolean add(MethodAndRoute methodAndRoute) {
            int length = methodAndRoute.getPath().length();
            if (length < minLength) {
                minLength = length;
                minLenUrl = methodAndRoute.getPath();
            }
            if (length > maxLength) {
                maxLength = length;
                maxLenUrl = methodAndRoute.getPath();
            }
            return super.add(methodAndRoute);
        }

        private String subString(int start, int until) {
            return get(0).getPath().substring(start, until);
        }

        private char charAt(int pos) {
            return get(0).getPath().charAt(pos);
        }

        private MatchStatus handleSlash(char curChar) {
            switch (curChar) {
                case '*':
                    return MatchStatus.WILD;
                case ':':
                    return MatchStatus.PARAM;
                default:
                    return MatchStatus.START;
            }
        }

        private boolean restAllBeginWithSlash() {
            for (MethodAndRoute methodAndRoute : this) {
                if (methodAndRoute.getPath().length() > getMinLength() &&
                        methodAndRoute.getPath().charAt(getMinLength()) != '/') {
                    return false;
                }
            }

            return true;
        }

        public void split(Node partRoot) {
            MatchStatus status = MatchStatus.START;
            // 最近一次发生状态转移的位置
            int lastStatusTransPos = offset;
            int cursor = offset;
            int j = 0;
            for (; cursor < getMinLength(); cursor++) {
                char prev = 0;
                boolean hasPrev = false;
                j = 0;
                for (; j < size(); j++) {
                    String curPath = get(j).getPath();

                    char c = curPath.charAt(cursor);
                    if (hasPrev && c != prev) {
                        break;
                    }
                    if (!hasPrev) {
                        hasPrev = true;
                        prev = c;
                    }
                }

                // handle split and conflict
                if (j != size()) {
                    if (status.canSplit) {
                        recurSplit(cursor, partRoot);
                        return;
                    } else {
                        throw new RuntimeException(
                                String.format("url %s conflict with %s",
                                        get(j).getPath(), get(j - 1).getPath())
                        );
                    }
                }

                char curChar = charAt(cursor);

                switch (status) {
                    case START:
                        if (curChar == ':' || curChar == '*') {
                            throw new RuntimeException(
                                    String.format("invalid url %s", get(0).getPath())
                            );
                        }
                        if (curChar == '/') {
                            lastStatusTransPos = cursor;
                            status = MatchStatus.SLASH;
                        }
                        break;
                    case SLASH:
                        // SLASH 状态不稳定, 必发生转换
                        lastStatusTransPos = cursor;
                        status = handleSlash(curChar);
                        break;
                    case WILD:
                        if (curChar == '/') {
                            // 进入终结状态
                            partRoot.setPart(subString(offset, cursor - 1));
                            Node wildChild = setStarChild(partRoot);
                            wildChild.setIndices(new char[]{'/'});
                            Node normalChild = new Node();
                            wildChild.setChildren(new Node[]{normalChild});

                            partRoot = normalChild;

                            offset = cursor;
                            lastStatusTransPos = cursor;
                            status = MatchStatus.SLASH;
                        } else {
                            throw new RuntimeException(String.format("invalid url %s", get(0).getPath()));
                        }
                        break;
                    case PARAM:
                        if (curChar == '*') {
                            throw new RuntimeException(String.format("invalid url %s", get(0).getPath()));
                        }
                        if (curChar == '/') {
                            partRoot.setPart(subString(offset, lastStatusTransPos));
                            partRoot.setIndices(new char[]{':'});

                            Node paramNode = new Node();
                            partRoot.setChildren(new Node[]{paramNode});
                            paramNode.setPart(subString(lastStatusTransPos, cursor));
                            paramNode.setIndices(new char[]{'/'});

                            Node normalNode = new Node();
                            paramNode.setChildren(new Node[]{normalNode});

                            partRoot = normalNode;

                            offset = cursor;
                            lastStatusTransPos = cursor;
                            status = MatchStatus.SLASH;
                        }
                        break;
                }
            }

            if (minLength != maxLength) {
                if (status.canSplit) {
                    recurSplit(cursor, partRoot);
                    return;
                } else if (status == MatchStatus.PARAM && restAllBeginWithSlash()) {
                    partRoot.setPart(subString(offset, lastStatusTransPos));
                    partRoot.setIndices(new char[]{':'});

                    Node paramNode = new Node();
                    partRoot.setChildren(new Node[]{paramNode});

                    offset = lastStatusTransPos;

                    recurSplit(cursor, paramNode);
                    return;
                } else {
                    throw new RuntimeException(
                            String.format("url %s conflict with %s",
                                    minLenUrl, maxLenUrl)
                    );
                }
            }

            // $ transfer
            switch (status) {
                case START:
                case SLASH:
                    partRoot.setPart(subString(offset, cursor));
                    Node curPartRoot = partRoot;
                    forEach(curPartRoot::addSupport);
                    break;
                case WILD:
                    // 进入终结状态
                    // catchAll 节点
                    // 特点: 有 support 的 wild 节点
                    // without *
                    partRoot.setPart(subString(offset, cursor - 1));
                    Node wildChild = setStarChild(partRoot);
                    forEach(wildChild::addSupport);
                    break;
                case PARAM:
                    partRoot.setPart(subString(offset, lastStatusTransPos));
                    partRoot.setIndices(new char[]{':'});

                    Node paramNode = new Node();
                    partRoot.setChildren(new Node[]{paramNode});
                    paramNode.setPart(subString(lastStatusTransPos, cursor));

                    forEach(paramNode::addSupport);
                    break;
            }

        }

        private Node setStarChild(Node parent) {
            Node wildChild = new Node();
            wildChild.setPart("*");
            parent.setChildren(new Node[]{wildChild});
            parent.setIndices(new char[]{'*'});
            return wildChild;
        }

        private void recurSplit(int cursor, Node partRoot) {
            List<MethodAndRoute> groupByTarget = this;
            if (cursor == getMinLength()) {
                Map<Boolean, List<MethodAndRoute>> partions = stream().collect(Collectors
                        .partitioningBy(mroute -> mroute.getPath().length() > getMinLength()));
                List<MethodAndRoute> rest = partions.get(true);
                groupByTarget = rest;
                List<MethodAndRoute> eliminator = partions.get(false);
                // add http method support to partRoot
                eliminator.forEach(partRoot::addSupport);
            }

            partRoot.setPart(subString(offset, cursor));
            if (groupByTarget.size() == 0) {
                return;
            }

            MethodAndRoutes[] splits = groupBy(groupByTarget, cursor);
            char[] indices = new char[splits.length];
            Node[] nodes = new Node[splits.length];
            for (int z = 0; z < indices.length; z++) {
                indices[z] = splits[z].get(0).getPath().charAt(cursor);
                nodes[z] = new Node();
            }
            partRoot.setIndices(indices);
            partRoot.setChildren(nodes);

            for (int i = 0; i < splits.length; i++) {
                splits[i].split(nodes[i]);
            }
        }

        public int getMinLength() {
            return minLength;
        }

    }

    @Value(staticConstructor = "of")
    private static class MethodAndRoute {
        private HttpMethod httpMethod;
        private Route route;
        private String path;
    }

    @Getter
    @Setter
    protected static class Node {
        // wild node's part is ':xxxx' or '*'
        private String part;

        // initials of children
        private char[] indices;

        private Node[] children;

        // bit map to HttpMethod
        // is null or byte[2]
        // if '*' node has byte[], this node can catch all tail
        //private byte[] supportMethodsBitMap;

        private Map<HttpMethod, Route> routeMap;

        public void addSupport(MethodAndRoute methodAndRoute) {
            if (routeMap == null) {
                routeMap = new HashMap<>();
            }
            routeMap.put(methodAndRoute.getHttpMethod(), methodAndRoute.getRoute());
        }

        public Route getSupport(HttpMethod httpMethod) {
            if (routeMap == null) {
                return null;
            }
            return routeMap.getOrDefault(httpMethod,
                    routeMap.get(HttpMethod.ALL));
        }

        public boolean isWildChild() {
            return indices != null &&
                    (indices[0] == ':' || indices[0] == '*');
        }
    }

    @Override
    public void addRoute(HttpMethod httpMethod, Route route, List<String> uriVariableNames) {
        registerCandidate.add(MethodAndRoute.of(httpMethod, route, route.getOriginalPath()));
    }

    @Override
    public void register() {
       registerCandidate.split(root);
    }

    private int forwardUntil(String str, int startFrom, char until) {
        int newOffset = str.indexOf(until, startFrom);
        return newOffset == -1? str.length(): newOffset;
    }

    private Route routeByMethod(Node node, HttpMethod httpMethod, Map<String, String> uriVariables) {
        Route supRoute = node.getSupport(httpMethod);
        if (supRoute != null) {
            supRoute.setPathParams(uriVariables);
        }
        return supRoute;
    }

    @Override
    public Route findRoute(String httpMethod, String path) {
        HttpMethod requestMethod = HttpMethod.valueOf(httpMethod);
        int offset = 0;
        Node cur = root;
        Map<String, String> uriVariables = new LinkedHashMap<>();

        while (offset < path.length()) {
            if (cur.getPart().equals("*")) {
                if (cur.getChildren() == null) {
                    // catch-all node
                    return routeByMethod(cur, requestMethod, uriVariables);
                }
                offset = forwardUntil(path, offset, '/');
                cur = cur.getChildren()[0];
                continue;
            } else if (cur.getPart().charAt(0) == ':') {
                int newOffset = forwardUntil(path, offset, '/');

                if (newOffset == path.length()) {
                    // 刚好匹配完
                    uriVariables.put(cur.getPart().substring(1), path.substring(offset, newOffset));
                    return routeByMethod(cur, requestMethod, uriVariables);
                } else if (cur.getChildren() != null) {
                    // 匹配到一个路径参数
                    uriVariables.put(cur.getPart().substring(1), path.substring(offset, newOffset));
                    cur = cur.getChildren()[0];
                    offset = newOffset;
                    continue;
                } else {
                    // 啥都没匹配到
                    return null;
                }
            } else {
                // 普通节点
                if ((path.length() - offset) < cur.getPart().length()) {
                    return null;
                }

                int end = offset + cur.getPart().length();
                if (!path.substring(offset, end).equals(cur.getPart())) {
                    return null;
                }

                offset = end;
                if (cur.isWildChild()) {
                    cur = cur.getChildren()[0];
                    continue;
                }

                for (int i = 0; i < cur.getIndices().length; i++) {
                    if (cur.getIndices()[i] == path.charAt(end)) {
                        cur = cur.getChildren()[i];
                        break;
                    }
                }
                continue;
            }
        }


        return null;
    }

    @Override
    public void clear() {
        root = new Node();
    }

}
