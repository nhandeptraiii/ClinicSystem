import { http } from './http';

type RestResponse<T> = {
  data: T;
  message?: string | null;
  statusCode?: number;
};

export interface DiagnosisRequest {
  symptoms: string[];
  topK?: number;
}

export interface DiseasePrediction {
  disease: string;
  probability: number;
  severity: string;
  shouldBookAppointment: boolean;
}

export interface DiagnosisResponse {
  predictions: DiseasePrediction[];
}

const unwrap = <T>(payload: RestResponse<T> | T): T => {
  if (payload && typeof payload === 'object' && 'data' in (payload as RestResponse<T>)) {
    return (payload as RestResponse<T>).data;
  }
  return payload as T;
};

export const analyzeSymptoms = async (payload: DiagnosisRequest) => {
  const requestPayload: DiagnosisRequest = {
    ...payload,
    topK: 10,
  };
  const { data } = await http.post<DiagnosisResponse | RestResponse<DiagnosisResponse>>(
    '/api/diagnosis/analyze',
    requestPayload
  );
  return unwrap(data);
};
