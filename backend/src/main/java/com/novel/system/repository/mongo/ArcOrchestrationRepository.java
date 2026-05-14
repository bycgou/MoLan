package com.novel.system.repository.mongo;

import com.novel.system.entity.mongo.ArcOrchestration;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArcOrchestrationRepository extends MongoRepository<ArcOrchestration, String> {

    /**
     * 根据卷ID查询编排计划
     */
    ArcOrchestration findByArcId(String arcId);

    /**
     * 删除指定卷的编排计划
     */
    void deleteByArcId(String arcId);
}
