/**
 * Copyright (c) 2014 Gang Ling.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
*/
package org.spdx.merge;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import org.spdx.rdfparser.InvalidSPDXAnalysisException;
import org.spdx.rdfparser.JavaSha1ChecksumGenerator;
import org.spdx.rdfparser.SPDXDocument;
import org.spdx.rdfparser.SPDXDocument.SPDXPackage;
import org.spdx.rdfparser.SPDXFile;
import org.spdx.rdfparser.SPDXLicenseInfo;
import org.spdx.rdfparser.SPDXLicenseInfoFactory;
import org.spdx.rdfparser.SpdxPackageVerificationCode;
import org.spdx.rdfparser.VerificationCodeGenerator;
import org.spdx.spdxspreadsheet.InvalidLicenseStringException;

/**
 * Application to merge package information from input SPDX documents and file information merging result.
 * @author Gang Ling
 *
 */
public class SpdxPackageInfoMerger {

		private SPDXDocument master = null;
		public SpdxPackageInfoMerger(SPDXDocument masterDoc){
			this.master = masterDoc;
		}
		
		/**
		 * 
		 * @param mergeDocs
		 * @param fileMergeResult
		 * @return
		 * @throws InvalidSPDXAnalysisException
		 * @throws NoSuchAlgorithmException
		 * @throws InvalidLicenseStringException 
		 */
		public SPDXPackage mergePackageInfo(SPDXDocument[] mergeDocs,SPDXFile[] fileMergeResult) 
				throws InvalidSPDXAnalysisException, NoSuchAlgorithmException, InvalidLicenseStringException{
			SPDXPackage packageMergeResult = master.getSpdxPackage().clone(master, master.getSpdxPackage().getDownloadUrl());
			
			String[] skippedFiles = collectSkippedFiles(mergeDocs);
			VerificationCodeGenerator vg = new VerificationCodeGenerator(new JavaSha1ChecksumGenerator());
			SpdxPackageVerificationCode result = vg.generatePackageVerificationCode(fileMergeResult, skippedFiles);
			packageMergeResult.setVerificationCode(result);
			
			SPDXLicenseInfo[] licsInFile = collectLicsInFiles(fileMergeResult);
			packageMergeResult.setLicenseInfoFromFiles(licsInFile);
			
			SPDXLicenseInfo declaredLicense = SPDXLicenseInfoFactory.parseSPDXLicenseString("NOASSERTION");
			packageMergeResult.setDeclaredLicense(declaredLicense);		
			
			String licComments = translateSubDelcaredLicsIntoComments(mergeDocs);
			packageMergeResult.setLicenseComment(licComments);
			
			
			return packageMergeResult;			
		}
		
		/**
		 * method to collect all skipped files from input SPDX documents
		 * @param spdxDoc
		 * @return
		 * @throws InvalidSPDXAnalysisException
		 */
		public String[] collectSkippedFiles(SPDXDocument[] mergeDocs) throws InvalidSPDXAnalysisException{
			ArrayList<String> excludedFileNamesList = new ArrayList<String>();
			for(int p = 0; p < mergeDocs.length; p++){
				String[] retval = mergeDocs[p].getSpdxPackage().getVerificationCode().getExcludedFileNames();
				
				if(excludedFileNamesList.size() == 0){
					for(int i = 0; i < retval.length; i++){
						excludedFileNamesList.add(i, retval[i]);
					}
				}else{
					for(int k = 0; k < retval.length; k++){
						boolean foundNameMatch = false;
						for(int q = 0; q < excludedFileNamesList.size(); q++){
							if(retval[k].equalsIgnoreCase(excludedFileNamesList.get(q))){
								foundNameMatch = true;
							}
						}
						if(!foundNameMatch){
							excludedFileNamesList.add(retval[k]);
						}
					}
				}
			}
			String[] excludedFileNamesArray = new String[excludedFileNamesList.size()];
			excludedFileNamesList.toArray(excludedFileNamesArray);
			excludedFileNamesList.clear();
			return excludedFileNamesArray;
		}
		
		/**
		 * method to collect all license information from file merging result
		 * @param fileMergeResult
		 * @return
		 */
		public SPDXLicenseInfo[] collectLicsInFiles(SPDXFile[] fileMergeResult){
			ArrayList<SPDXLicenseInfo> licsList = new ArrayList<SPDXLicenseInfo>();
			for(int a = 0; a < fileMergeResult.length; a++){
				SPDXLicenseInfo[] retval = fileMergeResult[a].getSeenLicenses();
				if(licsList.size() == 0){
					for(int b = 0; b < retval.length; b++){
						licsList.add(b, retval[b]);
					}
				}else{
					for(int c = 0; c < retval.length; c++){
						boolean foundLicMatch = false;
						for(int d = 0; d < licsList.size(); d++){
							if(retval[c].equals(licsList.get(d))){
								foundLicMatch = true;
							}
						}
						if(!foundLicMatch){
							licsList.add(retval[c]);
						}
					}
				}
			}
			SPDXLicenseInfo[] licsInFile = new SPDXLicenseInfo[licsList.size()];
			licsList.toArray(licsInFile);
			licsList.clear();
			return licsInFile;	
		}
		
		/**
		 * method to collect all declared licenses from sub-packages 
		 * and merge into string as license comments in merged package
		 * @param mergeDocs
		 * @return
		 */
		public String translateSubDelcaredLicsIntoComments(SPDXDocument[] mergeDocs){
			SpdxLicenseMapper mapper = new SpdxLicenseMapper(master);
			StringBuilder buffer = new StringBuilder("This package merged several packages and the sub-package contain the following licenses:");
			for(int k = 1; k < mergeDocs.length; k++){
				if(mapper.docInNonStdLicIdMap(mergeDocs[k])){
					SPDXPackage subPackage = null;
					SPDXLicenseInfo license = null;
					SPDXLicenseInfo result = null;
					try {
						subPackage = mergeDocs[k].getSpdxPackage();
						license = mergeDocs[k].getSpdxPackage().getDeclaredLicense();
						result = mapper.mapLicenseInfo(subPackage, license);
					} catch (InvalidSPDXAnalysisException e) {
						System.out.println("Error mapping declared licenses from sub document "+ mergeDocs[k]+ e.getMessage());
					}
					try {
						buffer.append(subPackage.getFileName());
					} catch (InvalidSPDXAnalysisException e) {
						System.out.println("Error getting package name from sub-document:" + mergeDocs[k]+ e.getMessage());
					}
					buffer.append(" (" + result.toString() + ") ");
				}
			}			
			return buffer.toString();		
		}
}
