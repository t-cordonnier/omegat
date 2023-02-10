/**************************************************************************
 OmegaT Plugin - OMT Package Manager

 Copyright (C) 2019 Briac Pilpré
 Home page: http://www.omegat.org/
 Support center: http://groups.yahoo.com/group/OmegaT/

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 **************************************************************************/

package org.omegat.core.pack.omt;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;

public class DirectoryFilter implements DirectoryStream.Filter<Path> {

    private final List<Pattern> excludePatterns = new ArrayList<>();
    private final Path projectRoot;

    public DirectoryFilter(Path projectRoot, List<String> excludePatterns) {
        this.projectRoot = projectRoot;
        for (String e : excludePatterns) {
            this.excludePatterns.add(Pattern.compile(e));
        }

        // Always exclude lock file, as it would cause the whole packing to fail
        this.excludePatterns.add(Pattern.compile("\\.lck$"));

        // Always exclude the log file as it will be included at the very end
        this.excludePatterns.add(Pattern.compile(ManageOMTPackage.OMT_PACKER_LOGNAME.replace(".", "\\.") + "$"));
    }

    @Override
    public boolean accept(Path entry) throws IOException {
        String matchEntry = FilenameUtils.normalizeNoEndSeparator(projectRoot.relativize(entry).toString(), true);
        for (Pattern excludePattern : excludePatterns) {
            if (excludePattern.matcher(matchEntry).find()) {
                ManageOMTPackage.omtPackLog(String.format("Exclude entry [%s] from regex [%s]", 
                    matchEntry, excludePattern.pattern()));
                return false;
            }
        }
        return true;
    }
}
