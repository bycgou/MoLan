package com.novel.system.repository.mongo;

import com.novel.system.entity.mongo.NovelGraph;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NovelGraphRepository extends MongoRepository<NovelGraph, String> {
}