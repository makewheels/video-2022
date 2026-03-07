declare module 'ali-oss' {
  interface OSSOptions {
    bucket: string;
    endpoint: string;
    accessKeyId: string;
    accessKeySecret: string;
    stsToken: string;
    secure?: boolean;
  }

  interface MultipartUploadOptions {
    parallel?: number;
    partSize?: number;
    progress?: (p: number, checkpoint: unknown) => void;
  }

  interface MultipartUploadResult {
    res: { status: number };
  }

  export default class OSS {
    constructor(options: OSSOptions);
    multipartUpload(
      key: string,
      file: File,
      options?: MultipartUploadOptions,
    ): Promise<MultipartUploadResult>;
  }
}
