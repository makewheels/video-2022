package com.github.makewheels.video2022.file;

import com.github.makewheels.usermicroservice2022.User;
import org.apache.commons.io.FilenameUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;

@Service
public class FileService {
    @Resource
    private MongoTemplate mongoTemplate;

    public File create(User user, String originalFilename) {
        File file = new File();
        file.setUserId(user.getId());
        file.setOriginalFilename(originalFilename);
        file.setExtension(FilenameUtils.getExtension(originalFilename).toLowerCase());
        file.setCreateTime(new Date());
        mongoTemplate.save(file);
        return file;
    }

}
