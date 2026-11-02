// 30 seconds of GET /api/v1/loans. BASE_URL defaults to the Compose app.
import http from "k6/http";
import { check } from "k6";

export const options = { vus: 20, duration: "30s" };

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

export default function () {
  const response = http.get(`${BASE_URL}/api/v1/loans`);
  check(response, { "status is 200": (r) => r.status === 200 });
}
