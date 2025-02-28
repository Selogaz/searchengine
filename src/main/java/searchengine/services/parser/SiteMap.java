package searchengine.services.parser;


import lombok.Getter;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;



@Getter
public class SiteMap {
    private String url;
    private String domain;
    private CopyOnWriteArrayList<SiteMap> siteMapChildrens;

    public SiteMap(String url) {
        siteMapChildrens = new CopyOnWriteArrayList<>();
        this.url = url;
        this.domain = getProtocolAndDomain(url);
    }

    public void addChildren(SiteMap children) {
        siteMapChildrens.add(children);
    }

    public static String getProtocolAndDomain(String url) {
        String regEx = "(^https:\\/\\/)(?:[^@\\/\\n]+@)?(?:www\\.)?([^:\\/\\n]+)";
        ByteBuffer buffer = StandardCharsets.UTF_8.encode(regEx);
        String utf8EncodedString = StandardCharsets.UTF_8.decode(buffer).toString();
        Pattern pattern = Pattern.compile(utf8EncodedString);
        return pattern.matcher(url)
                .results()
                .map(m -> m.group(1) + m.group(2))
                .findFirst()
                .orElseThrow();
    }

}
