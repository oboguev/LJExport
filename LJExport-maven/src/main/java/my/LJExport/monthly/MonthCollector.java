package my.LJExport.monthly;

import my.LJExport.readers.direct.PageParserDirectBase;

/*
 * Collects posts within a month that fall to the same style.
 * Different page styles go to different collectors. 
 */
public class MonthCollector
{
    // record id of the first page
    int first_rid;
    
    // cleaned-up HEAD tag
    String cleanedHead;
    
    // collecting parser
    PageParserDirectBase parser;
}
