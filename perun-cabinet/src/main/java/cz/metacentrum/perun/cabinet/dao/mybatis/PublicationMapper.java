package cz.metacentrum.perun.cabinet.dao.mybatis;

import cz.metacentrum.perun.cabinet.model.Publication;
import cz.metacentrum.perun.cabinet.model.PublicationForGUI;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

public interface PublicationMapper {

	/**
	 * This method was generated by MyBatis Generator.
	 * This method corresponds to the database table PUBLICATION
	 *
	 * @mbggenerated Fri Nov 04 19:27:43 CET 2011
	 */
	int countByExample(PublicationExample example);

	/**
	 * This method was generated by MyBatis Generator.
	 * This method corresponds to the database table PUBLICATION
	 *
	 * @mbggenerated Fri Nov 04 19:27:43 CET 2011
	 */
	int deleteByExample(PublicationExample example);

	/**
	 * This method was generated by MyBatis Generator.
	 * This method corresponds to the database table PUBLICATION
	 *
	 * @mbggenerated Fri Nov 04 19:27:43 CET 2011
	 */
	int deleteByPrimaryKey(Integer id);

	/**
	 * This method was generated by MyBatis Generator.
	 * This method corresponds to the database table PUBLICATION
	 *
	 * @mbggenerated Fri Nov 04 19:27:43 CET 2011
	 */
	int insert(Publication record);

	/**
	 * This method was generated by MyBatis Generator.
	 * This method corresponds to the database table PUBLICATION
	 *
	 * @mbggenerated Fri Nov 04 19:27:43 CET 2011
	 */
	int insertInternal(Publication record);

	/**
	 * This method was generated by MyBatis Generator.
	 * This method corresponds to the database table PUBLICATION
	 *
	 * @mbggenerated Fri Nov 04 19:27:43 CET 2011
	 */
	int insertSelective(Publication record);

	/**
	 * This method was generated by MyBatis Generator.
	 * This method corresponds to the database table PUBLICATION
	 *
	 * @mbggenerated Fri Nov 04 19:27:43 CET 2011
	 */
	List<Publication> selectByExample(PublicationExample example);

	/**
	 * This method was generated by MyBatis Generator.
	 * This method corresponds to the database table PUBLICATION
	 *
	 * @mbggenerated Fri Nov 04 19:27:43 CET 2011
	 */
	List<Publication> selectByANDExample(PublicationExample example);

	/**
	 * This method was generated by MyBatis Generator.
	 * This method corresponds to the database table PUBLICATION
	 *
	 * @mbggenerated Fri Nov 04 19:27:43 CET 2011
	 */
	Publication selectByPrimaryKey(Integer id);

	/**
	 * This method was generated by MyBatis Generator.
	 * This method corresponds to the database table PUBLICATION
	 *
	 * @mbggenerated Fri Nov 04 19:27:43 CET 2011
	 */
	int updateByExampleSelective(@Param("record") Publication record, @Param("example") PublicationExample example);

	/**
	 * This method was generated by MyBatis Generator.
	 * This method corresponds to the database table PUBLICATION
	 *
	 * @mbggenerated Fri Nov 04 19:27:43 CET 2011
	 */
	int updateByExample(@Param("record") Publication record, @Param("example") PublicationExample example);

	/**
	 * This method was generated by MyBatis Generator.
	 * This method corresponds to the database table PUBLICATION
	 *
	 * @mbggenerated Fri Nov 04 19:27:43 CET 2011
	 */
	int updateByPrimaryKeySelective(Publication record);

	/**
	 * This method was generated by MyBatis Generator.
	 * This method corresponds to the database table PUBLICATION
	 *
	 * @mbggenerated Fri Nov 04 19:27:43 CET 2011
	 */
	int updateByPrimaryKey(Publication record);

	List<Publication> selectByFilter(Publication p);

	List<Publication> selectByParams(Map<String, Object> params);

	PublicationForGUI selectRichByPrimaryKey(Integer publicationId);
	
	List<PublicationForGUI> selectRichByFilter(@Param("pub") Publication p,@Param("user") Integer userId);
	
	List<PublicationForGUI> selectRichByANDExample(@Param("pub") PublicationExample example,@Param("user") Integer userId);
	
	int lockPublications(@Param("lockState") boolean lockState, @Param("ids") List<Integer> ids);
	
}