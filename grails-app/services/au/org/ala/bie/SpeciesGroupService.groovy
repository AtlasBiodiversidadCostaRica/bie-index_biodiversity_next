package au.org.ala.bie

import au.org.ala.bie.indexing.RankedName
import au.org.ala.bie.indexing.SubGroup
import com.google.common.io.Resources
import groovy.json.JsonSlurper

import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

class SpeciesGroupService {

    static transactional = false

    def grailsApplication
    def fileWatchService
    Map<RankedName, SubGroup> invertedSpeciesGroups
    /**
     * Eagerly loads the species group JSON file and, if the json file location is set, reloads the file on changes.
     */
    @PostConstruct
    def init() {
        if (location) {
            fileWatchService.watch(location) {
                log.info("Reloading species groups")
                loadInvertedSpeciesGroupMap()
            }
        }
        loadInvertedSpeciesGroupMap()
    }

    @PreDestroy
    def close() {
        fileWatchService.unwatch(location)
    }

    def configFileDetails() {
        long size
        long lastModified
        InputStream is
        if (location) {
            def f = new File(location)
            lastModified = f.lastModified()
            size = f.length()
            is = f.newInputStream()
        } else {
            def r = Resources.getResource('speciesGroups.json')
            def c = r.openConnection()
            lastModified = c.lastModified
            size = c.contentLength
            is = r.newInputStream()
        }
        return [size: size, lastModified: lastModified, is: is]
    }

    private String getLocation() {
        grailsApplication.config?.species?.groups?.location ?: ''
    }

    private void loadInvertedSpeciesGroupMap() {
        invertedSpeciesGroups = invertSpeciesGroups(loadSpeciesGroups())
    }

    private static Map<RankedName, SubGroup> invertSpeciesGroups(speciesGroups) {
        speciesGroups.collectEntries { group ->
            group.taxa.collectEntries { taxon ->
                [ (new RankedName(name: taxon.name?.toLowerCase(), rank: ImportService.normaliseRank(group.taxonRank))): new SubGroup(group: group.speciesGroup, subGroup: taxon.common) ]
            }
        }
    }

    private def loadSpeciesGroups() {
        def speciesGroups
        if (location) {
            try {
                speciesGroups = loadSpeciesGroups(new File(location).newInputStream())
            } catch (IOException e) {
                log.error("Could not load species groups from $location. Falling back to built in speciesGroups.json", e)
                speciesGroups = loadSpeciesGroups(Resources.getResource('speciesGroups.json').newInputStream())
            }
        } else {
            log.info("No species group provided, using built in speciesGroups.json")
            speciesGroups = loadSpeciesGroups(Resources.getResource('speciesGroups.json').newInputStream())
        }
        return speciesGroups
    }

    private static def loadSpeciesGroups(InputStream inputStream) {
        inputStream.withStream {
            def js = new JsonSlurper()
            js.parseText(inputStream.text)
        }
    }
}
