/*
 * hhadump.c  v0.2
 *      
 * Copyright 2008-2009 Bob Thomas <bob@pleep.com>
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at 
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 *      
 */


#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <sys/stat.h>
#include <sys/types.h>


//file metadata structure
struct hha_file_info {
	int dir;		//directory name (offset into filename list)
	int name;		//file (offset into filename list)
	int compress;	//compression level (0-2)
	int offset;		//offset from start of file
	int full_len;	//uncompressed file size
	int len;		//file size in archive
};

//hha file header and metadata
struct hha_file {
	int magic;			//0xAC2FF34F
	int version;		//0x00010000, might represent "0.1.0.0"
	int filenames_len;	//length of filename list
	int num_files;		//length of hha_file_info array
	
	char *filenames;	//filename list
	struct hha_file_info *fileinfo;	//file metadata
};

//tidy up before exiting
void cleanup(struct hha_file *file, FILE *fp) {
	free(file->filenames);
	free(file->fileinfo);
	fclose(fp);
}

static int create_dir(char *path) {
    char *p;

    for (p = path + 1; *p != '\0'; ++p) {
        if (*p == '/') {
             *p = '\0';
             if (mkdir(path, 0755) && errno != EEXIST)
                return 1;
             *p = '/';
         }
     }
     if (mkdir(path, 0755) && errno != EEXIST)
         return 1;
	 return 0;
}

int main(int argc, char **argv) {
	FILE *fp;
	struct hha_file file;
	int i, len;
	
	if (argc < 2) {
		printf("Usage: %s filename.hha\n", argv[0]);
		return 1;
	}
	
	fp = fopen(argv[1], "rb");
	if (!fp) {
		perror("Cannot open file");
		return 2;
	}
	
	len = fread(&file, sizeof(int), 4, fp);
	if (len != 4) {
		perror("Cannot read from file");
		fclose(fp);
		return 3;
	}
	
	if (file.magic != 0xAC2FF34F) {
		printf("File %s is not an HHA file :(\n", argv[1]);
		fclose(fp);
		return 4;
	}
	
	printf("Magic number is: %X\n", file.magic);
	printf("Version is: %x\n", file.version);
	printf("Length of filenames: %d\n", file.filenames_len);
	printf("Number of files: %d\n", file.num_files);
	
	file.filenames = malloc(file.filenames_len);
	file.fileinfo = malloc(file.num_files * sizeof(struct hha_file_info));
	
	len = fread(file.filenames, 1, file.filenames_len, fp);
	if (len != file.filenames_len) {
		perror("Could not read filename list");
		cleanup(&file, fp);
		return 5;
	}
	len = fread(file.fileinfo, sizeof(struct hha_file_info), file.num_files, fp);
	if (len != file.num_files) {
		perror("Could not read file metadata");
		cleanup(&file, fp);
		return 6;
	}
	
	for (i = 0; i < file.num_files; i++) {
		char *dirname, *filename;
		dirname = file.filenames + file.fileinfo[i].dir;
		filename = file.filenames + file.fileinfo[i].name;
		
		if (file.fileinfo[i].compress == 0) {
			FILE *nfp;
			char *buf, *fullname;
			
			printf("Extracting %s/%s\n", dirname, filename);
			
			if (fseek(fp, file.fileinfo[i].offset, SEEK_SET)) {
				perror("Could not seek to file data");
				continue;
			}
			
			fullname = malloc(strlen(dirname) + strlen(filename) + 2);
			sprintf(fullname, "%s/%s", dirname, filename);
			
			buf = malloc(file.fileinfo[i].len);
			len = fread(buf, file.fileinfo[i].len, 1, fp);
			
			nfp = fopen(fullname, "wb");
			if (!nfp) {
				//this usually means the directory does not exist
				char dir[256];
				strcpy(dir, dirname);
				if(create_dir(dir)) {
					perror("Could not create directory");
					return 7;
				}
				nfp = fopen(fullname, "wb");
			}
			
			if (!nfp) {
				perror("Could not open file for writing");
			} else {
				fwrite(buf, file.fileinfo[i].len, 1, nfp);
				fclose(nfp);
			}
			
			free(buf);
			free(fullname);
		} else {
			//Compression type 1 is deflate/zlib
			//Compression type 2 is LZMA
			//too lazy to implement here
			printf("Skipping file %s/%s, Compression #%d, %d/%d\n", dirname, filename, file.fileinfo[i].compress, file.fileinfo[i].len, file.fileinfo[i].full_len);
		}
	}
	
	cleanup(&file, fp);
	return 0;
}
