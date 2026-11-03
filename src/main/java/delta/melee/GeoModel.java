package delta.melee;

import com.google.gson.*;
import java.io.Reader;
import java.util.*;

/** Parses a Bedrock/GeckoLib .geo.json and bakes it into oriented textured quads (block units). Pure Java, no Minecraft deps. */
public final class GeoModel {
    /** Flip to true if rotations look mirrored in-game (GeckoLib negates X/Y rotation). */
    public static boolean NEGATE_XY_ROT = false;

    /** 4 vertices * (x,y,z,u,v) + normal(3) = 23 floats per quad. */
    public final List<float[]> quads = new ArrayList<>();
    public float minX = 1e9f, minY = 1e9f, minZ = 1e9f, maxX = -1e9f, maxY = -1e9f, maxZ = -1e9f;

    private static final class Bone { String name, parent; double[] pivot = {0,0,0}, rot; JsonArray cubes; }
    /** affine transform: p' = R p + t */
    private static final class Aff {
        double[][] r = {{1,0,0},{0,1,0},{0,0,1}}; double[] t = {0,0,0};
        static Aff rotAbout(double[] rot, double[] pv) {
            Aff a = new Aff();
            double rx = Math.toRadians(NEGATE_XY_ROT ? -rot[0] : rot[0]);
            double ry = Math.toRadians(NEGATE_XY_ROT ? -rot[1] : rot[1]);
            double rz = Math.toRadians(rot[2]);
            double[][] X = {{1,0,0},{0,Math.cos(rx),-Math.sin(rx)},{0,Math.sin(rx),Math.cos(rx)}};
            double[][] Y = {{Math.cos(ry),0,Math.sin(ry)},{0,1,0},{-Math.sin(ry),0,Math.cos(ry)}};
            double[][] Z = {{Math.cos(rz),-Math.sin(rz),0},{Math.sin(rz),Math.cos(rz),0},{0,0,1}};
            a.r = mul(Z, mul(Y, X)); // X first, then Y, then Z (Bedrock order)
            double[] rp = apply(a.r, pv);
            for (int i = 0; i < 3; i++) a.t[i] = pv[i] - rp[i];
            return a;
        }
        double[] ap(double[] p) { double[] o = apply(r, p); for (int i = 0; i < 3; i++) o[i] += t[i]; return o; }
    }
    static double[][] mul(double[][] a, double[][] b) {
        double[][] c = new double[3][3];
        for (int i=0;i<3;i++) for (int j=0;j<3;j++) for (int k=0;k<3;k++) c[i][j]+=a[i][k]*b[k][j];
        return c;
    }
    static double[] apply(double[][] m, double[] p) {
        return new double[]{ m[0][0]*p[0]+m[0][1]*p[1]+m[0][2]*p[2], m[1][0]*p[0]+m[1][1]*p[1]+m[1][2]*p[2], m[2][0]*p[0]+m[2][1]*p[1]+m[2][2]*p[2] };
    }

    private static boolean hidden(Bone b, Map<String, Bone> bones) {
        for (Bone x = b; x != null; x = bones.get(x.parent)) {
            String n = x.name.toLowerCase(Locale.ROOT);
            if (n.contains("hand") || n.equals("camera") || n.equals("view") || n.equals("idle_view")
                || n.equals("constraint") || n.equals("positioning") || n.equals("ground") || n.equals("fixed")) return true;
        }
        return false;
    }
    private static double[] arr(JsonElement e, double[] def) {
        if (e == null || !e.isJsonArray()) return def;
        JsonArray a = e.getAsJsonArray(); double[] o = new double[a.size()];
        for (int i = 0; i < o.length; i++) o[i] = a.get(i).getAsDouble();
        return o;
    }

    /** @param orient optional {rotX, rotY, rotZ} degrees applied first (to point the blade along +Y); null = auto */
    public static GeoModel load(Reader reader, double[] orient) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        JsonObject geo = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject desc = geo.getAsJsonObject("description");
        double tw = desc.get("texture_width").getAsDouble(), th = desc.get("texture_height").getAsDouble();
        Map<String, Bone> bones = new LinkedHashMap<>();
        for (JsonElement e : geo.getAsJsonArray("bones")) {
            JsonObject o = e.getAsJsonObject(); Bone b = new Bone();
            b.name = o.get("name").getAsString();
            b.parent = o.has("parent") ? o.get("parent").getAsString() : null;
            b.pivot = arr(o.get("pivot"), b.pivot); b.rot = arr(o.get("rotation"), null);
            b.cubes = o.has("cubes") ? o.getAsJsonArray("cubes") : new JsonArray();
            bones.put(b.name, b);
        }
        // pass 1: collect world-space (pixel) quads
        List<double[][]> faces = new ArrayList<>(); List<double[][]> uvs = new ArrayList<>();
        for (Bone b : bones.values()) {
            if (hidden(b, bones) || b.cubes.size() == 0) continue;
            List<Aff> chain = new ArrayList<>();
            for (Bone x = b; x != null; x = bones.get(x.parent)) if (x.rot != null) chain.add(Aff.rotAbout(x.rot, x.pivot));
            for (JsonElement ce : b.cubes) {
                JsonObject c = ce.getAsJsonObject();
                double[] o = arr(c.get("origin"), null), s = arr(c.get("size"), null);
                double inf = c.has("inflate") ? c.get("inflate").getAsDouble() : 0;
                if (c.has("rotation")) chain.add(0, Aff.rotAbout(arr(c.get("rotation"), null), arr(c.get("pivot"), new double[]{0,0,0})));
                double x0=o[0]-inf,y0=o[1]-inf,z0=o[2]-inf,x1=o[0]+s[0]+inf,y1=o[1]+s[1]+inf,z1=o[2]+s[2]+inf;
                // face order: north south east west up down; verts TL TR BR BL as seen from outside
                double[][][] fv = {
                    {{x1,y1,z0},{x0,y1,z0},{x0,y0,z0},{x1,y0,z0}},
                    {{x0,y1,z1},{x1,y1,z1},{x1,y0,z1},{x0,y0,z1}},
                    {{x1,y1,z1},{x1,y1,z0},{x1,y0,z0},{x1,y0,z1}},
                    {{x0,y1,z0},{x0,y1,z1},{x0,y0,z1},{x0,y0,z0}},
                    {{x0,y1,z0},{x1,y1,z0},{x1,y1,z1},{x0,y1,z1}},
                    {{x0,y0,z1},{x1,y0,z1},{x1,y0,z0},{x0,y0,z0}}};
                String[] names = {"north","south","east","west","up","down"};
                double w=s[0],h=s[1],d=s[2];
                double[][] boxUv = null; // {u,v,uw,vh} per face
                if (c.has("uv") && c.get("uv").isJsonArray()) {
                    double[] uv = arr(c.get("uv"), null); double u=uv[0], v=uv[1];
                    boolean mir = c.has("mirror") && c.get("mirror").getAsBoolean();
                    boxUv = new double[][]{
                        {u+d, v+d, w, h}, {u+d+w+d, v+d, w, h},
                        mir ? new double[]{u+d+w, v+d, d, h} : new double[]{u, v+d, d, h},
                        mir ? new double[]{u, v+d, d, h} : new double[]{u+d+w, v+d, d, h},
                        {u+d, v, w, d}, {u+d+w, v, w, d}};
                }
                for (int f = 0; f < 6; f++) {
                    double[] r;
                    if (boxUv != null) r = boxUv[f];
                    else if (c.has("uv") && c.getAsJsonObject("uv").has(names[f])) {
                        JsonObject fo = c.getAsJsonObject("uv").getAsJsonObject(names[f]);
                        double[] a = arr(fo.get("uv"), null), z = arr(fo.get("uv_size"), new double[]{0,0});
                        r = new double[]{a[0], a[1], z[0], z[1]};
                    } else continue;
                    if (r[2] == 0 || r[3] == 0) continue;
                    double[][] q = new double[4][];
                    for (int i = 0; i < 4; i++) { double[] p = fv[f][i].clone(); for (Aff a : chain) p = a.ap(p); q[i] = p; }
                    faces.add(q);
                    double u0 = r[0]/tw, v0 = r[1]/th, u1 = (r[0]+r[2])/tw, v1 = (r[1]+r[3])/th;
                    uvs.add(new double[][]{{u0,v0},{u1,v0},{u1,v1},{u0,v1}});
                }
                if (c.has("rotation")) chain.remove(0);
            }
        }
        GeoModel m = new GeoModel();
        if (faces.isEmpty()) return m;
        // pass 2: orient. user/auto orientation brings the longest axis onto +Y, thin axis onto Z
        double[][] ori = orient != null ? Aff.rotAbout(orient, new double[]{0,0,0}).r : autoOrient(faces);
        for (double[][] q : faces) for (int i = 0; i < 4; i++) q[i] = apply(ori, q[i]);
        // then lay blade along the sprite diagonal like vanilla handheld items: rotate -45deg about Z
        double[][] rz = Aff.rotAbout(new double[]{0,0,-45}, new double[]{0,0,0}).r;
        for (double[][] q : faces) for (int i = 0; i < 4; i++) q[i] = apply(rz, q[i]);
        // place handle end at lower-left of the 16x16 cell, centre on the diagonal and on Z
        double dmin = 1e9, pmin = 1e9, pmax = -1e9, zmin = 1e9, zmax = -1e9;
        for (double[][] q : faces) for (double[] p : q) {
            double along = (p[0] + p[1]) / Math.sqrt(2), perp = (p[1] - p[0]) / Math.sqrt(2);
            dmin = Math.min(dmin, along); pmin = Math.min(pmin, perp); pmax = Math.max(pmax, perp);
            zmin = Math.min(zmin, p[2]); zmax = Math.max(zmax, p[2]);
        }
        double shiftAlong = -6.5 * Math.sqrt(2) - dmin, shiftPerp = -(pmin + pmax) / 2, shiftZ = -(zmin + zmax) / 2;
        double k = Math.sqrt(0.5);
        double dx = (shiftAlong - shiftPerp) * k, dy = (shiftAlong + shiftPerp) * k;
        for (int fi = 0; fi < faces.size(); fi++) {
            double[][] q = faces.get(fi); double[][] uv = uvs.get(fi);
            float[] out = new float[23];
            for (int i = 0; i < 4; i++) {
                float x = (float)((q[i][0] + dx) / 16), y = (float)((q[i][1] + dy) / 16), z = (float)((q[i][2] + shiftZ) / 16);
                out[i*5] = x; out[i*5+1] = y; out[i*5+2] = z; out[i*5+3] = (float)uv[i][0]; out[i*5+4] = (float)uv[i][1];
                m.minX = Math.min(m.minX, x); m.maxX = Math.max(m.maxX, x);
                m.minY = Math.min(m.minY, y); m.maxY = Math.max(m.maxY, y);
                m.minZ = Math.min(m.minZ, z); m.maxZ = Math.max(m.maxZ, z);
            }
            double[] e1 = sub(q[1], q[0]), e2 = sub(q[3], q[0]);
            double[] n = {e1[1]*e2[2]-e1[2]*e2[1], e1[2]*e2[0]-e1[0]*e2[2], e1[0]*e2[1]-e1[1]*e2[0]};
            double len = Math.sqrt(n[0]*n[0]+n[1]*n[1]+n[2]*n[2]); if (len == 0) len = 1;
            // outward normal: (TR-TL) x (BL-TL) points inward for our winding, so negate
            out[20] = (float)(-n[0]/len); out[21] = (float)(-n[1]/len); out[22] = (float)(-n[2]/len);
            // rotate normal with the same -45deg/orient rotation already applied to vertices (computed from them) - fine
            m.quads.add(out);
        }
        return m;
    }
    private static double[] sub(double[] a, double[] b) { return new double[]{a[0]-b[0], a[1]-b[1], a[2]-b[2]}; }

    /** Longest bbox axis -> +Y, thinnest -> Z (axis permutation only, keeps handedness via sign fix). */
    private static double[][] autoOrient(List<double[][]> faces) {
        double[] mn = {1e9,1e9,1e9}, mx = {-1e9,-1e9,-1e9};
        for (double[][] q : faces) for (double[] p : q) for (int i = 0; i < 3; i++) { mn[i]=Math.min(mn[i],p[i]); mx[i]=Math.max(mx[i],p[i]); }
        double[] ext = {mx[0]-mn[0], mx[1]-mn[1], mx[2]-mn[2]};
        Integer[] idx = {0,1,2}; Arrays.sort(idx, (a,b)->Double.compare(ext[b], ext[a]));
        int up = idx[0], thin = idx[2], mid = idx[1];
        // new X <- mid axis, new Y <- longest axis, new Z <- thinnest axis
        double[][] m = new double[3][3]; m[0][mid]=1; m[1][up]=1; m[2][thin]=1;
        double det = m[0][0]*(m[1][1]*m[2][2]-m[1][2]*m[2][1]) - m[0][1]*(m[1][0]*m[2][2]-m[1][2]*m[2][0]) + m[0][2]*(m[1][0]*m[2][1]-m[1][1]*m[2][0]);
        if (det < 0) m[2][thin] = -1; // keep it a proper rotation
        return m;
    }
}
