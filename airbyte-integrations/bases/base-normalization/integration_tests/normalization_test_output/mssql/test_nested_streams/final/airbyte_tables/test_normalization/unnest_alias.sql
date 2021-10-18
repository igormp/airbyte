
   
  USE [test_normalization];
  if object_id ('test_normalization."unnest_alias__dbt_tmp_temp_view"','V') is not null
      begin
      drop view test_normalization."unnest_alias__dbt_tmp_temp_view"
      end


   
   
  USE [test_normalization];
  if object_id ('test_normalization."unnest_alias__dbt_tmp"','U') is not null
      begin
      drop table test_normalization."unnest_alias__dbt_tmp"
      end


   USE [test_normalization];
   EXEC('create view test_normalization."unnest_alias__dbt_tmp_temp_view" as
    
-- Final base SQL model
select
    id,
    children,
    _airbyte_emitted_at,
    _airbyte_unnest_alias_hashid
from "test_normalization"._airbyte_test_normalization."unnest_alias_ab3"
-- unnest_alias from "test_normalization".test_normalization._airbyte_raw_unnest_alias
    ');

   SELECT * INTO "test_normalization".test_normalization."unnest_alias__dbt_tmp" FROM
    "test_normalization".test_normalization."unnest_alias__dbt_tmp_temp_view"

   
   
  USE [test_normalization];
  if object_id ('test_normalization."unnest_alias__dbt_tmp_temp_view"','V') is not null
      begin
      drop view test_normalization."unnest_alias__dbt_tmp_temp_view"
      end

    
   use [test_normalization];
  if EXISTS (
        SELECT * FROM
        sys.indexes WHERE name = 'test_normalization_unnest_alias__dbt_tmp_cci'
        AND object_id=object_id('test_normalization_unnest_alias__dbt_tmp')
    )
  DROP index test_normalization.unnest_alias__dbt_tmp.test_normalization_unnest_alias__dbt_tmp_cci
  CREATE CLUSTERED COLUMNSTORE INDEX test_normalization_unnest_alias__dbt_tmp_cci
    ON test_normalization.unnest_alias__dbt_tmp

   

