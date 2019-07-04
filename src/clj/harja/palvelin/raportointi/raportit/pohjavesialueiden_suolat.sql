-- name: urakan-pohjavesialueet
SELECT nimi,tunnus FROM pohjavesialueet_urakoittain WHERE urakka=:urakka;

-- name: pohjavesialueen-tiekohtaiset-summat
SELECT SUM(pv_st.yhteensa)              AS yhteensa,
       pv_st.tie,
       pv_st.alkuosa,
       pv_st.alkuet,
       pv_st.loppuosa,
       pv_st.loppuet,
       (array_agg(pv_st.tunnus))[1]     AS tunnus,
       (array_agg(pv_st.pituus))[1]     AS pituus,
       (array_agg(pv_st.kayttoraja))[1] AS kayttoraja
FROM raportti_pohjavesialueiden_suolatoteumat pv_st
WHERE pv_st."urakka-id" = :urakka AND
      pv_st.paiva BETWEEN :alkupvm AND :loppupvm
GROUP BY pv_st.tie, pv_st.alkuosa, pv_st.alkuet, pv_st.loppuosa, pv_st.loppuet;