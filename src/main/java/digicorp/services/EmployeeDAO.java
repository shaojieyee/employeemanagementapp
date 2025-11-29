package digicorp.services;

import digicorp.dto.*;
import digicorp.entity.Employee;
import digicorp.entity.TitleHistory;
import digicorp.entity.TitleHistoryId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.EntityTransaction;
import java.time.LocalDate;

import java.util.List;

public class EmployeeDAO {
    protected EntityManager em;
    public EmployeeDAO(EntityManager em) {
        this.em = em;
    }
    // Fetch full employee with history using JOIN FETCH
    public Employee findByIdWithHistory(int empNo) {
        TypedQuery<Employee> q = em.createQuery(
                "SELECT DISTINCT e FROM Employee e " +
                        "LEFT JOIN FETCH e.departments de " +
                        "LEFT JOIN FETCH de.department d " +
                        "WHERE e.empNo = :empNo",
                Employee.class
        );

        q.setParameter("empNo", empNo);

        Employee e = q.getSingleResult();

        // Optional: lazy load manager history if your endpoint needs it
        e.getManagedDepartments().size();

        return e;
    }

    public List<EmployeeRecordDTO> findByDepartment(String deptNo, int page) {
        int pageSize = 20;
        int offset = (page - 1) * pageSize;

        String jpql =
                "SELECT new digicorp.dto.EmployeeRecordDTO(" +
                        "   e.empNo, e.firstName, e.lastName, e.hireDate" +
                        ") " +
                        "FROM DeptEmployee de " +
                        "JOIN de.employee e " +
                        "WHERE de.id.deptNo = :deptNo " +
                        "ORDER BY e.empNo ASC";

        TypedQuery<EmployeeRecordDTO> query = em.createQuery(jpql, EmployeeRecordDTO.class);
        query.setParameter("deptNo", deptNo);
        query.setFirstResult(offset);
        query.setMaxResults(pageSize);

        return query.getResultList();
    }


    public void promoteEmployee(PromotionRequestDTO dto) throws Exception {
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();

            // 1. Find the employee
            Employee emp = em.find(Employee.class, dto.getEmpNo());
            if (emp == null) {
                throw new Exception("Employee not found with empNo: " + dto.getEmpNo());
            }

            LocalDate newFromDate = dto.getFromDate();

            // 2. Find current title(s) for this employee
            TypedQuery<TitleHistory> query = em.createQuery(
                    "SELECT t FROM TitleHistory t WHERE t.employee.empNo = :empNo AND t.toDate = :maxDate",
                    TitleHistory.class
            );
            query.setParameter("empNo", emp.getEmpNo());
            query.setParameter("maxDate", LocalDate.of(9999, 1, 1));
            List<TitleHistory> currentTitles = query.getResultList();

            // 3. Update previous title(s) to end the day before the new promotion
            for (TitleHistory t : currentTitles) {
                t.setToDate(newFromDate.minusDays(1));
                em.merge(t);
            }

            // 4. Add new title record
            TitleHistoryId newId = new TitleHistoryId(emp.getEmpNo(), dto.getNewTitle(), newFromDate);
            TitleHistory newTitle = new TitleHistory();
            newTitle.setId(newId);
            newTitle.setEmployee(emp);
            newTitle.setToDate(LocalDate.of(9999, 1, 1));

            em.persist(newTitle);

            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

}