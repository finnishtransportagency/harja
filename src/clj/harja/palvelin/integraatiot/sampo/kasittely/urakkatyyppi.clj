(ns harja.palvelin.integraatiot.sampo.kasittely.urakkatyyppi)

(defn paattele-yllapidon-tyyppi [alueurakkanumero]
  (if (< 2 (count alueurakkanumero))
    (let [tunniste (subs alueurakkanumero 2 3)]
      (case tunniste
        "V" "valaistus"
        "P" "paallystys"
        "T" "tiemerkinta"
        "S" "siltakorjaus"
        "hoito"))
    "hoito"))

(defn paattele-urakkatyyppi [alueurakkanumero]
  (if (< 1 (count alueurakkanumero))
    (let [tunniste (subs alueurakkanumero 1 2)]
      (case tunniste
        "H" "hoito"
        "Y" (paattele-yllapidon-tyyppi alueurakkanumero)
        "hoito"))
    "hoito"))

(defn paattele-liikennemuoto [alueurakkanumero]
  (if (empty? alueurakkanumero)
    "t"
    (let [tunniste (subs alueurakkanumero 0 1)]
      (case tunniste
        "T" "t"
        "R" "r"
        "V" "v"
        "t"))))





