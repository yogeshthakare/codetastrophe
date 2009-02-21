/*
 * HHA support routines for PhysicsFS.
 *
 * This driver handles Hothead archives (HHA), developed by
 * Hothead Games, Inc.
 * 
 * Compression types deciphered by Maks Verver <maksverver@geocities.com>
 * 
 * Based on grp.c and zip.c
 */

//#if (defined PHYSFS_SUPPORTS_HHA)

/* TODO move this */
# define HHA_ARCHIVE_DESCRIPTION "Hothead archive"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#ifndef _WIN32_WCE
#include <errno.h>
#endif
#include "physfs.h"
#include "zlib.h"

#define __PHYSICSFS_INTERNAL__
#include "physfs_internal.h"

/* see zip.c for explanation */
#define ZIP_READBUFSIZE   (16 * 1024)

#define HHA_COMPRESS_NONE 0
#define HHA_COMPRESS_ZLIB 1
#define HHA_COMPRESS_LZMA 2

/* magic numbers */
#define HHA_FILE_MAGIC   0xAC2FF34F
#define HHA_FILE_VERSION 0x00010000

/* file metadata structure */
typedef struct
{
	char *dir;              /* directory name */
	char *name;             /* file name */
	PHYSFS_uint32 compress; /* compression level (0-2) */
	PHYSFS_uint32 offset; /* offset from start of file */
    PHYSFS_uint32 uncompressed_size;  /* compressed size */
    PHYSFS_uint32 compressed_size;    /* uncompressed size */
} HHAentry;

/* HHA file header and metadata */
typedef struct
{
	PHYSFS_uint32 entryCount;     /* number of files */
	
	char *filenames;             /* filename list */
	HHAentry *entries;           /* file metadata */
	
	/* filesystem info for PhysFS */
	char *filename;
	PHYSFS_sint64 last_mod_time;
} HHAinfo;

typedef struct
{
    void *handle;
    HHAentry *entry;
    PHYSFS_uint32 compressed_position;    /* offset in compressed data. */
    PHYSFS_uint32 uncompressed_position;  /* tell() position.           */
    PHYSFS_uint8 *buffer;                 /* decompression buffer.      */
	z_stream zlib_stream;                 /* zlib stream state.         */
	/*TODO LZMA stream */
} HHAfileinfo;

/* ZLIB functions copied from zip.c */

/*
 * Bridge physfs allocation functions to zlib's format...
 */
static voidpf zlibPhysfsAlloc(voidpf opaque, uInt items, uInt size)
{
    return(((PHYSFS_Allocator *) opaque)->Malloc(items * size));
} /* zlibPhysfsAlloc */

/*
 * Bridge physfs allocation functions to zlib's format...
 */
static void zlibPhysfsFree(voidpf opaque, voidpf address)
{
    ((PHYSFS_Allocator *) opaque)->Free(address);
} /* zlibPhysfsFree */


/*
 * Construct a new z_stream to a sane state.
 */
static void initializeZStream(z_stream *pstr)
{
    memset(pstr, '\0', sizeof (z_stream));
    pstr->zalloc = zlibPhysfsAlloc;
    pstr->zfree = zlibPhysfsFree;
    pstr->opaque = &allocator;
} /* initializeZStream */


static const char *zlib_error_string(int rc)
{
    switch (rc)
    {
        case Z_OK: return(NULL);  /* not an error. */
        case Z_STREAM_END: return(NULL); /* not an error. */
#ifndef _WIN32_WCE
        case Z_ERRNO: return(strerror(errno));
#endif
        case Z_NEED_DICT: return(ERR_NEED_DICT);
        case Z_DATA_ERROR: return(ERR_DATA_ERROR);
        case Z_MEM_ERROR: return(ERR_MEMORY_ERROR);
        case Z_BUF_ERROR: return(ERR_BUFFER_ERROR);
        case Z_VERSION_ERROR: return(ERR_VERSION_ERROR);
        default: return(ERR_UNKNOWN_ERROR);
    } /* switch */

    return(NULL);
} /* zlib_error_string */


/*
 * Wrap all zlib calls in this, so the physfs error state is set appropriately.
 */
static int zlib_err(int rc)
{
    const char *str = zlib_error_string(rc);
    if (str != NULL)
        __PHYSFS_setError(str);
    return(rc);
} /* zlib_err */


static void HHA_dirClose(dvoid *opaque)
{
    HHAinfo *info = ((HHAinfo *) opaque);
    allocator.Free(info->filename);
	allocator.Free(info->filenames);
    allocator.Free(info->entries);
    allocator.Free(info);
} /* HHA_dirClose */


static PHYSFS_sint64 HHA_read(fvoid *opaque, void *buffer,
                              PHYSFS_uint32 objSize, PHYSFS_uint32 objCount)
{
    HHAfileinfo *finfo = (HHAfileinfo *) opaque;
    HHAentry *entry = finfo->entry;
    PHYSFS_sint64 retval = 0;
    PHYSFS_sint64 maxread = ((PHYSFS_sint64) objSize) * objCount;
    PHYSFS_sint64 avail = entry->uncompressed_size -
                          finfo->uncompressed_position;

    BAIL_IF_MACRO(maxread == 0, NULL, 0);    /* quick rejection. */

    if (avail < maxread)
    {
        maxread = avail - (avail % objSize);
        objCount = (PHYSFS_uint32) (maxread / objSize);
        BAIL_IF_MACRO(objCount == 0, ERR_PAST_EOF, 0);  /* quick rejection. */
        __PHYSFS_setError(ERR_PAST_EOF);   /* this is always true here. */
    } /* if */


    if (entry->compress == HHA_COMPRESS_NONE)
	{
        retval = __PHYSFS_platformRead(finfo->handle, buffer, objSize, objCount);
	}
	else if (entry->compress == HHA_COMPRESS_ZLIB)
	{
		finfo->zlib_stream.next_out = buffer;
        finfo->zlib_stream.avail_out = objSize * objCount;

        while (retval < maxread)
        {
            PHYSFS_uint32 before = finfo->zlib_stream.total_out;
            int rc;

            if (finfo->zlib_stream.avail_in == 0)
            {
                PHYSFS_sint64 br;

                br = entry->compressed_size - finfo->compressed_position;
                if (br > 0)
                {
                    if (br > ZIP_READBUFSIZE)
                        br = ZIP_READBUFSIZE;

                    br = __PHYSFS_platformRead(finfo->handle,
                                               finfo->buffer,
                                               1, (PHYSFS_uint32) br);
                    if (br <= 0)
                        break;

                    finfo->compressed_position += (PHYSFS_uint32) br;
                    finfo->zlib_stream.next_in = finfo->buffer;
                    finfo->zlib_stream.avail_in = (PHYSFS_uint32) br;
                } /* if */
            } /* if */

            rc = zlib_err(inflate(&finfo->zlib_stream, Z_SYNC_FLUSH));
            retval += (finfo->zlib_stream.total_out - before);

            if (rc != Z_OK)
                break;
        } /* while */

        retval /= objSize;
	}
    if (retval > 0)
        finfo->uncompressed_position += (PHYSFS_uint32) (retval * objSize);

    return(retval);
} /* HHA_read */


static PHYSFS_sint64 HHA_write(fvoid *opaque, const void *buffer,
                               PHYSFS_uint32 objSize, PHYSFS_uint32 objCount)
{
    BAIL_MACRO(ERR_NOT_SUPPORTED, -1);
} /* HHA_write */


static int HHA_eof(fvoid *opaque)
{
    HHAfileinfo *finfo = (HHAfileinfo *) opaque;
    return(finfo->uncompressed_position >= finfo->entry->uncompressed_size);
} /* HHA_eof */


static PHYSFS_sint64 HHA_tell(fvoid *opaque)
{
    return(((HHAfileinfo *) opaque)->uncompressed_position);
} /* HHA_tell */


static int HHA_seek(fvoid *opaque, PHYSFS_uint64 offset)
{
    HHAfileinfo *finfo = (HHAfileinfo *) opaque;
    HHAentry *entry = finfo->entry;
	void *in = finfo->handle;

    BAIL_IF_MACRO(offset < 0, ERR_INVALID_ARGUMENT, 0);
    BAIL_IF_MACRO(offset >= entry->uncompressed_size, ERR_PAST_EOF, 0);
	if (entry->compress == HHA_COMPRESS_NONE)
	{
        PHYSFS_sint64 newpos = offset + entry->offset;
        BAIL_IF_MACRO(!__PHYSFS_platformSeek(in, newpos), NULL, 0);
        finfo->uncompressed_position = (PHYSFS_uint32) offset;
	}
    else if (entry->compress == HHA_COMPRESS_ZLIB)
    {
        /*
         * If seeking backwards, we need to redecode the file
         *  from the start and throw away the compressed bits until we hit
         *  the offset we need. If seeking forward, we still need to
         *  decode, but we don't rewind first.
         */
        if (offset < finfo->uncompressed_position)
        {
            /* we do a copy so state is sane if inflateInit2() fails. */
            z_stream str;
            initializeZStream(&str);
            if (zlib_err(inflateInit2(&str, -MAX_WBITS)) != Z_OK)
                return(0);

            if (!__PHYSFS_platformSeek(in, entry->offset))
                return(0);

            inflateEnd(&finfo->zlib_stream);
            memcpy(&finfo->zlib_stream, &str, sizeof (z_stream));
            finfo->uncompressed_position = finfo->compressed_position = 0;
        } /* if */

        while (finfo->uncompressed_position != offset)
        {
            PHYSFS_uint8 buf[512];
            PHYSFS_uint32 maxread;

            maxread = (PHYSFS_uint32) (offset - finfo->uncompressed_position);
            if (maxread > sizeof (buf))
                maxread = sizeof (buf);

            if (HHA_read(finfo, buf, maxread, 1) != 1)
                return(0);
        } /* while */
    } /* else */
	
    return(1);
} /* HHA_seek */


static PHYSFS_sint64 HHA_fileLength(fvoid *opaque)
{
    HHAfileinfo *finfo = (HHAfileinfo *) opaque;
    return((PHYSFS_sint64) finfo->entry->uncompressed_size);
} /* HHA_fileLength */


static int HHA_fileClose(fvoid *opaque)
{
    HHAfileinfo *finfo = (HHAfileinfo *) opaque;
    BAIL_IF_MACRO(!__PHYSFS_platformClose(finfo->handle), NULL, 0);
	
	if (finfo->entry->compress == HHA_COMPRESS_ZLIB)
        inflateEnd(&finfo->zlib_stream);

    if (finfo->buffer != NULL)
        allocator.Free(finfo->buffer);
	
    allocator.Free(finfo);
    return(1);
} /* HHA_fileClose */


static int hha_open(const char *filename, int forWriting,
                    void **fh, PHYSFS_uint32 *filenameSize, PHYSFS_uint32 *count)
{
    PHYSFS_uint32 magic[2];

    *fh = NULL;
	
    BAIL_IF_MACRO(forWriting, ERR_ARC_IS_READ_ONLY, 0);
    
    *fh = __PHYSFS_platformOpenRead(filename);
    BAIL_IF_MACRO(*fh == NULL, NULL, 0);
    
    if (__PHYSFS_platformRead(*fh, magic, sizeof(PHYSFS_uint32), 2) != 2)
        goto openHHA_failed;

	magic[0] = PHYSFS_swapULE32(magic[0]);
	magic[1] = PHYSFS_swapULE32(magic[1]);
    if (!(magic[0] == HHA_FILE_MAGIC && magic[1] == HHA_FILE_VERSION))
    {
        __PHYSFS_setError(ERR_UNSUPPORTED_ARCHIVE);
        goto openHHA_failed;
    } /* if */
	
	if (__PHYSFS_platformRead(*fh, filenameSize, sizeof (PHYSFS_uint32), 1) != 1)
        goto openHHA_failed;
    *filenameSize = PHYSFS_swapULE32(*filenameSize);
    if (__PHYSFS_platformRead(*fh, count, sizeof (PHYSFS_uint32), 1) != 1)
        goto openHHA_failed;
    *count = PHYSFS_swapULE32(*count);

    return(1);

openHHA_failed:
    if (*fh != NULL)
        __PHYSFS_platformClose(*fh);

    *filenameSize = -1;
    *count = -1;
    *fh = NULL;
    return(0);
} /* HHA_open */


static int HHA_isArchive(const char *filename, int forWriting)
{
    void *fh;
	PHYSFS_uint32 nameSize;
    PHYSFS_uint32 fileCount;
    int retval = hha_open(filename, forWriting, &fh, &nameSize, &fileCount);

    if (fh != NULL)
        __PHYSFS_platformClose(fh);

    return(retval);
} /* HHA_isArchive */


static int HHA_entry_cmp(void *_a, PHYSFS_uint32 one, PHYSFS_uint32 two)
{
    if (one != two)
    {
		int retval = 0;
        const HHAentry *a = (const HHAentry *) _a;
		retval = strcmp(a[one].dir, a[two].dir);
		if (!retval)
		    retval = strcmp(a[one].name, a[two].name);
		return retval;
    } /* if */

    return 0;
} /* HHA_entry_cmp */


static void HHA_entry_swap(void *_a, PHYSFS_uint32 one, PHYSFS_uint32 two)
{
    if (one != two)
    {
        HHAentry tmp;
        HHAentry *first = &(((HHAentry *) _a)[one]);
        HHAentry *second = &(((HHAentry *) _a)[two]);
        memcpy(&tmp, first, sizeof (HHAentry));
        memcpy(first, second, sizeof (HHAentry));
        memcpy(second, &tmp, sizeof (HHAentry));
    } /* if */
} /* HHA_entry_swap */


static int HHA_load_entries(const char *name, int forWriting, HHAinfo *info)
{
    void *fh = NULL;
	PHYSFS_uint32 fileNameSize;
    PHYSFS_uint32 fileCount;
    HHAentry *entry;
	PHYSFS_uint32 buf[6];

    BAIL_IF_MACRO(!hha_open(name, forWriting, &fh, &fileNameSize, &fileCount), NULL, 0);
    info->entryCount = fileCount;
	info->filenames = (char *) allocator.Malloc(fileNameSize);
	if (info->filenames == NULL)
    {
        __PHYSFS_platformClose(fh);
        BAIL_MACRO(ERR_OUT_OF_MEMORY, 0);
    } /* if */
    info->entries = (HHAentry *) allocator.Malloc(sizeof(HHAentry)*fileCount);
    if (info->entries == NULL)
    {
        __PHYSFS_platformClose(fh);
        BAIL_MACRO(ERR_OUT_OF_MEMORY, 0);
    } /* if */
	
	if (__PHYSFS_platformRead(fh, info->filenames, 1, fileNameSize) != fileNameSize)
	{
        __PHYSFS_platformClose(fh);
        return(0);
    } 

    for (entry = info->entries; fileCount > 0; fileCount--, entry++)
    {
        if (__PHYSFS_platformRead(fh, buf, sizeof(PHYSFS_uint32), 6) != 6)
        {
            __PHYSFS_platformClose(fh);
            return(0);
        } /* if */

        entry->dir = info->filenames + PHYSFS_swapULE32(buf[0]);
		entry->name = info->filenames + PHYSFS_swapULE32(buf[1]);
		entry->compress = PHYSFS_swapULE32(buf[2]);
		entry->offset = PHYSFS_swapULE32(buf[3]);
		entry->uncompressed_size = PHYSFS_swapULE32(buf[4]);
        entry->compressed_size = PHYSFS_swapULE32(buf[5]);
    } /* for */

    __PHYSFS_platformClose(fh);

    __PHYSFS_sort(info->entries, info->entryCount,
                  HHA_entry_cmp, HHA_entry_swap);
    return(1);
} /* HHA_load_entries */


static void *HHA_openArchive(const char *name, int forWriting)
{
    PHYSFS_sint64 modtime = __PHYSFS_platformGetLastModTime(name);
    HHAinfo *info = (HHAinfo *) allocator.Malloc(sizeof (HHAinfo));

    BAIL_IF_MACRO(info == NULL, ERR_OUT_OF_MEMORY, 0);

    memset(info, '\0', sizeof (HHAinfo));
    info->filename = (char *) allocator.Malloc(strlen(name) + 1);
    GOTO_IF_MACRO(!info->filename, ERR_OUT_OF_MEMORY, HHA_openArchive_failed);

    if (!HHA_load_entries(name, forWriting, info))
        goto HHA_openArchive_failed;

    strcpy(info->filename, name);
    info->last_mod_time = modtime;

    return(info);

HHA_openArchive_failed:
    if (info != NULL)
    {
        if (info->filename != NULL)
            allocator.Free(info->filename);
        if (info->entries != NULL)
            allocator.Free(info->entries);
        allocator.Free(info);
    } /* if */

    return(NULL);
} /* HHA_openArchive */


static HHAentry *HHA_find_entry(HHAinfo *info, const char *name)
{
    HHAentry *a = info->entries;
    PHYSFS_sint32 lo = 0;
    PHYSFS_sint32 hi = (PHYSFS_sint32) (info->entryCount - 1);
    PHYSFS_sint32 middle;
	char dirname[256];
	char filename[256];
    int rc;
	
	char *fpart = strrchr(name, '/');
	if (fpart != NULL)
	{
		strcpy(filename, fpart + 1);
		strncpy(dirname, name, fpart - name);
		dirname[fpart - name] = '\0';
	}
	else
	{
		strcpy(dirname, name);
		filename[0] = '\0';
	}

    while (lo <= hi)
    {
        middle = lo + ((hi - lo) / 2);
		rc = strcmp(dirname, a[middle].dir);
		if (rc == 0 && filename[0])
	        rc = strcmp(filename, a[middle].name);
        if (rc == 0)  /* found it! */
            return(&a[middle]);
        else if (rc > 0)
            lo = middle + 1;
        else
            hi = middle - 1;
    } /* while */

    BAIL_MACRO(ERR_NO_SUCH_FILE, NULL);
} /* HHA_find_entry */

static int HHA_isDirectory(dvoid *opaque, const char *name, int *fileExists)
{
	HHAinfo *info = (HHAinfo *) opaque;
    HHAentry *a = info->entries;
    PHYSFS_sint32 lo = 0;
    PHYSFS_sint32 hi = (PHYSFS_sint32) (info->entryCount - 1);
    PHYSFS_sint32 middle;
	int rc;
	
	/* emulate finding a directory by finding a file in the directory */
	
	while (lo <= hi)
    {
        middle = lo + ((hi - lo) / 2);
		rc = strcmp(name, a[middle].dir);
        if (rc == 0)  /* found it! */
		{
			*fileExists = 1;
            return(1);
		}
        else if (rc > 0)
            lo = middle + 1;
        else
            hi = middle - 1;
    } /* while */
	
	*fileExists = (HHA_find_entry((HHAinfo *) opaque, name) != NULL);
	return(0);
} /* HHA_isDirectory */

static int HHA_exists(dvoid *opaque, const char *name)
{
	/* check if file or directory */
	int exists;
    (void)HHA_isDirectory(opaque, name, &exists);
	return exists;
} /* HHA_exists */

static int HHA_isSymLink(dvoid *opaque, const char *name, int *fileExists)
{
    *fileExists = HHA_exists(opaque, name);
    return(0);  /* never symlinks in HHA. */
} /* HHA_isSymLink */

/*
 * Moved to seperate function so we can use alloca then immediately throw
 *  away the allocated stack space...
 */
static void doEnumCallback(PHYSFS_EnumFilesCallback cb, void *callbackdata,
                           const char *odir, const char *str, PHYSFS_sint32 ln)
{
    char *newstr = __PHYSFS_smallAlloc(ln + 1);
    if (newstr == NULL)
        return;

    memcpy(newstr, str, ln);
    newstr[ln] = '\0';
    cb(callbackdata, odir, newstr);
    __PHYSFS_smallFree(newstr);
} /* doEnumCallback */

static void HHA_enumerateFiles(dvoid *opaque, const char *dname,
                               int omitSymLinks, PHYSFS_EnumFilesCallback cb,
                               const char *origdir, void *callbackdata)
{
	size_t dlen = strlen(dname),
           dlen_inc = dlen + ((dlen > 0) ? 1 : 0);
    HHAinfo *info = (HHAinfo *) opaque;
    HHAentry *entry = info->entries;
	HHAentry *lastEntry = &info->entries[info->entryCount];
	char lastDir[256];
	lastDir[0] = '\0';
	
	if (dlen)
	{
		while (entry < lastEntry)
		{
			if (!strncmp(dname, entry->dir, dlen))
			    break;
			entry++;
		}
    }
	
	
	while (entry < lastEntry)
	{
		if(!strcmp(dname, entry->dir))
		    doEnumCallback(cb, callbackdata, origdir, entry->name, strlen(entry->name));
		
		if(strlen(entry->dir) > dlen)
		{
		    char *fname = entry->dir + dlen_inc;
		    char *dirNameEnd = strchr(fname, '/');
		    size_t dirNameLen = dirNameEnd - fname;
		    if (dirNameEnd == NULL)
		        dirNameLen = strlen(fname);
		    if (dlen && strncmp(dname, entry->dir, dlen))
		        break;
		    if (strncmp(lastDir, fname, dirNameLen))
		    {
		        doEnumCallback(cb, callbackdata, origdir, fname, dirNameLen);
			    strncpy(lastDir, fname, dirNameLen);
				lastDir[dirNameLen] = '\0';
		    }
		}
		entry++;
	}

} /* HHA_enumerateFiles */

static PHYSFS_sint64 HHA_getLastModTime(dvoid *opaque,
                                        const char *name,
                                        int *fileExists)
{
    HHAinfo *info = (HHAinfo *) opaque;
    PHYSFS_sint64 retval = -1;

    *fileExists = (HHA_find_entry(info, name) != NULL);
    if (*fileExists)  /* use time of HHA itself in the physical filesystem. */
        retval = info->last_mod_time;

    return(retval);
} /* HHA_getLastModTime */


static fvoid *HHA_openRead(dvoid *opaque, const char *fnm, int *fileExists)
{
    HHAinfo *info = (HHAinfo *) opaque;
    HHAfileinfo *finfo;
    HHAentry *entry;

    entry = HHA_find_entry(info, fnm);
    *fileExists = (entry != NULL);
    BAIL_IF_MACRO(entry == NULL, NULL, NULL);

    finfo = (HHAfileinfo *) allocator.Malloc(sizeof (HHAfileinfo));
    BAIL_IF_MACRO(finfo == NULL, ERR_OUT_OF_MEMORY, NULL);
    memset(finfo, '\0', sizeof (HHAfileinfo));
    finfo->handle = __PHYSFS_platformOpenRead(info->filename);
    if ( (finfo->handle == NULL) ||
         (!__PHYSFS_platformSeek(finfo->handle, entry->offset)) )
    {
        allocator.Free(finfo);
        return(NULL);
    } /* if */
	
    finfo->entry = entry;
	
    if (finfo->entry->compress == HHA_COMPRESS_ZLIB)
    {
	    initializeZStream(&finfo->zlib_stream);
        if (zlib_err(inflateInit2(&finfo->zlib_stream, -MAX_WBITS)) != Z_OK)
        {
            HHA_fileClose(finfo);
            return(NULL);
        } /* if */

        finfo->buffer = (PHYSFS_uint8 *) allocator.Malloc(ZIP_READBUFSIZE);
        if (finfo->buffer == NULL)
        {
            HHA_fileClose(finfo);
            BAIL_MACRO(ERR_OUT_OF_MEMORY, NULL);
        } /* if */
    } /* if */
	
    return(finfo);
} /* HHA_openRead */


static fvoid *HHA_openWrite(dvoid *opaque, const char *name)
{
    BAIL_MACRO(ERR_NOT_SUPPORTED, NULL);
} /* HHA_openWrite */


static fvoid *HHA_openAppend(dvoid *opaque, const char *name)
{
    BAIL_MACRO(ERR_NOT_SUPPORTED, NULL);
} /* HHA_openAppend */


static int HHA_remove(dvoid *opaque, const char *name)
{
    BAIL_MACRO(ERR_NOT_SUPPORTED, 0);
} /* HHA_remove */


static int HHA_mkdir(dvoid *opaque, const char *name)
{
    BAIL_MACRO(ERR_NOT_SUPPORTED, 0);
} /* HHA_mkdir */


const PHYSFS_ArchiveInfo __PHYSFS_ArchiveInfo_HHA =
{
    "HHA",
    HHA_ARCHIVE_DESCRIPTION,
    "Bob Thomas <bob@pleep.com>",
    "http://icculus.org/physfs/",
};


const PHYSFS_Archiver __PHYSFS_Archiver_HHA =
{
    &__PHYSFS_ArchiveInfo_HHA,
    HHA_isArchive,          /* isArchive() method      */
    HHA_openArchive,        /* openArchive() method    */
    HHA_enumerateFiles,     /* enumerateFiles() method */
    HHA_exists,             /* exists() method         */
    HHA_isDirectory,        /* isDirectory() method    */
    HHA_isSymLink,          /* isSymLink() method      */
    HHA_getLastModTime,     /* getLastModTime() method */
    HHA_openRead,           /* openRead() method       */
    HHA_openWrite,          /* openWrite() method      */
    HHA_openAppend,         /* openAppend() method     */
    HHA_remove,             /* remove() method         */
    HHA_mkdir,              /* mkdir() method          */
    HHA_dirClose,           /* dirClose() method       */
    HHA_read,               /* read() method           */
    HHA_write,              /* write() method          */
    HHA_eof,                /* eof() method            */
    HHA_tell,               /* tell() method           */
    HHA_seek,               /* seek() method           */
    HHA_fileLength,         /* fileLength() method     */
    HHA_fileClose           /* fileClose() method      */
};

//#endif  /* defined PHYSFS_SUPPORTS_HHA */

/* end of hha.c ... */

