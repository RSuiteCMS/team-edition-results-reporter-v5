<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:param name="rsuite.serverurl"/>
    <xsl:param name="rsuite.sessionkey"/>
    <xsl:param name="report.excel.url"/>
    <xsl:param name="report.stylesheet.url"/>

    <xsl:template match="/">
        <html>
            <head>
                <title>RSuite Content Report</title>
                <link rel="stylesheet" type="text/css" href="{$report.stylesheet.url}"/>
                <script type="text/javascript" src="/rsuite/rest/v1/static/team-edition-results-reporter/scripts/sorttable.js"/>
            </head>
            <body>
                <!--<xsl:copy-of select="report"/>-->
                <xsl:apply-templates select="report/(reportTitle|reportUserInfo)" mode="title"/>
                <p>Download as <a href="{$report.excel.url}">Excel</a></p>
                <xsl:apply-templates/>
                <xsl:apply-templates select="report/reportFields" mode="title"/>
            </body>
        </html>
    </xsl:template>
    <xsl:template match="report">
        <xsl:apply-templates/>
    </xsl:template>
    <xsl:template match="reportTitle"/>
    <xsl:template match="reportTitle" mode="title">
        <h2>
            <xsl:apply-templates/>
        </h2>
    </xsl:template>
    <xsl:template match="reportUserInfo"/>
    <xsl:template match="reportUserInfo" mode="title">
        <p class="userInfo">
            <xsl:apply-templates/>
        </p>
    </xsl:template>
    <xsl:template match="reportFields"/>
    <xsl:template match="reportFields" mode="title">
        <p class="reportFields">
            <b>Report fields: </b>
            <span class="reportFields"><xsl:apply-templates/></span>
        </p>
    </xsl:template>
    <xsl:template match="table">
        <table width="100%" class="sortable">
            <xsl:apply-templates/>
        </table>
    </xsl:template>
    <xsl:template match="tr">
        <tr>
            <xsl:apply-templates/>
        </tr>
    </xsl:template>
    <xsl:template match="th">
        <th>
            <xsl:apply-templates/>
        </th>
    </xsl:template>
    <xsl:template match="td">
        <xsl:variable name="val" select="text()"/>
        <td>
            <xsl:choose>
                <xsl:when test="starts-with($val, '/rsuite/rest/')">
                    <img src="{$val}" alt="thumbnail" height="50px"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:for-each select="tokenize($val, '\{\{brk\}\}')">
                        <xsl:copy-of select="."/>
                        <xsl:if test="position() &lt; last()">
                            <br/>
                        </xsl:if>
                    </xsl:for-each>
                </xsl:otherwise>
            </xsl:choose>
        </td>
    </xsl:template>
</xsl:stylesheet>
