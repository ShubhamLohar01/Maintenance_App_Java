package com.candorfoods.factoryops.data.local.dao

import androidx.room.*
import com.candorfoods.factoryops.data.local.entity.PmTemplateEntity
import com.candorfoods.factoryops.data.local.entity.PmTemplateItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PmTemplateDao {

    @Query("SELECT * FROM pm_templates WHERE isActive = 1 ORDER BY name ASC")
    fun observeActiveTemplates(): Flow<List<PmTemplateEntity>>

    @Query("SELECT * FROM pm_templates WHERE machineType = :machineType AND isActive = 1 ORDER BY name ASC")
    fun observeByMachineType(machineType: String): Flow<List<PmTemplateEntity>>

    @Query("SELECT * FROM pm_templates WHERE id = :id")
    suspend fun getTemplateById(id: String): PmTemplateEntity?

    @Query("SELECT * FROM pm_templates WHERE id = :id")
    fun observeTemplateById(id: String): Flow<PmTemplateEntity?>

    @Query("SELECT * FROM pm_template_items WHERE templateId = :templateId ORDER BY orderIndex ASC")
    fun observeItems(templateId: String): Flow<List<PmTemplateItemEntity>>

    @Query("SELECT * FROM pm_template_items WHERE templateId = :templateId ORDER BY orderIndex ASC")
    suspend fun getItemsForTemplate(templateId: String): List<PmTemplateItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplate(template: PmTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<PmTemplateItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplates(templates: List<PmTemplateEntity>)

    @Query("DELETE FROM pm_template_items WHERE templateId = :templateId")
    suspend fun deleteItemsForTemplate(templateId: String)

    @Transaction
    suspend fun replaceTemplateWithItems(template: PmTemplateEntity, items: List<PmTemplateItemEntity>) {
        upsertTemplate(template)
        deleteItemsForTemplate(template.id)
        upsertItems(items)
    }

    @Query("SELECT COUNT(*) FROM pm_templates")
    suspend fun count(): Int
}
